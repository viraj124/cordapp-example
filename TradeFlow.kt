package com.example.flow

import co.paralleluniverse.fibers.Suspendable
import com.example.contract.TradeContract
import com.example.flow.TradeFlow.Acceptor
import com.example.flow.TradeFlow.Initiator
import com.example.state.TradeState
import net.corda.core.contracts.Command
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.ProgressTracker.Step
import java.sql.Timestamp

/**
 * This flow allows two parties (the [Initiator] and the [Acceptor]) to come to an agreement about the IOU encapsulated
 * within an [TradeState].
 *
 * In our simple example, the [Acceptor] always accepts a valid IOU.
 *
 * These flows have deliberately been implemented by using only the call() method for ease of understanding. In
 * practice we would recommend splitting up the various stages of the flow into sub-routines.
 *
 * All methods called within the [FlowLogic] sub-class need to be annotated with the @Suspendable annotation.
 */
object TradeFlow {
    @InitiatingFlow
    @StartableByRPC
    class Initiator(val sellValue: Int,
    val sellCurrency: String,
    val buyValue: Int,
    val buyCurrency: String,
    val tradeStatus: String,
    val counterParty: Party,
    val regulator: Party,
    val userId: String,
    val assetCode: String,
    val orderType: String,
    val transactionAmount: Int,
    val transactionFees: Int,
    val transactionUnits: Int,
    val timestamp: Timestamp) : FlowLogic<SignedTransaction>() {
        /**
         * The progress tracker checkpoints each stage of the flow and outputs the specified messages when each
         * checkpoint is reached in the code. See the 'progressTracker.currentStep' expressions within the call() function.
         */
        companion object {
            object GENERATING_TRANSACTION : Step("Generating transaction based on new IOU.")
            object VERIFYING_TRANSACTION : Step("Verifying contract constraints.")
            object SIGNING_TRANSACTION : Step("Signing transaction with our private key.")
            object GATHERING_SIGS : Step("Gathering the counterparty's signature.") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }

            object FINALISING_TRANSACTION : Step("Obtaining notary signature and recording transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(
                    GENERATING_TRANSACTION,
                    VERIFYING_TRANSACTION,
                    SIGNING_TRANSACTION,
                    GATHERING_SIGS,
                    FINALISING_TRANSACTION
            )
        }

        override val progressTracker = tracker()

        /**
         * The flow logic is encapsulated within the call() method.
         */
        @Suspendable
        override fun call(): SignedTransaction {
            // Obtain a reference to the notary we want to use.
            val notary = serviceHub.networkMapCache.notaryIdentities[0]

            // Stage 1.
            progressTracker.currentStep = GENERATING_TRANSACTION
            // Generate an unsigned transaction.
            val tradeState = TradeState(serviceHub.myInfo.legalIdentities.first(), counterParty,tradeStatus,regulator,userId,assetCode,orderType,transactionAmount,transactionFees,transactionUnits,timestamp)
            val txCommand = Command(TradeContract.Commands.Create(), listOf(ourIdentity.owningKey,counterParty.owningKey))
            val txBuilder = TransactionBuilder(notary)
                    .addOutputState(tradeState, TradeContract.ID)
                    .addCommand(txCommand)
            // Stage 2.
            progressTracker.currentStep = VERIFYING_TRANSACTION

            // Verify that the transaction is valid.
            txBuilder.verify(serviceHub)

            // Stage 3.
            progressTracker.currentStep = SIGNING_TRANSACTION

            // Sign the transaction.
            val partSignedTx = serviceHub.signInitialTransaction(txBuilder)

            // Stage 4.
            progressTracker.currentStep = GATHERING_SIGS

            // Send the state to the counterparty, and receive it back with their signature.
            val otherPartyFlow = initiateFlow(counterParty)
            val regulatorFlow = initiateFlow(regulator)

            val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, setOf(otherPartyFlow), GATHERING_SIGS.childProgressTracker()))

            // Stage 5.
            progressTracker.currentStep = FINALISING_TRANSACTION
            // Notarise and record the transaction in both parties' vaults.
            return subFlow(FinalityFlow(fullySignedTx, setOf(otherPartyFlow, regulatorFlow), FINALISING_TRANSACTION.childProgressTracker()))
        }
    }

    @InitiatedBy(Initiator::class)
    open class Acceptor(val otherPartyFlow: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(otherPartyFlow) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val output = stx.tx.outputs.single().data
                    val trade = output as TradeState
                    val x500Name = CordaX500Name.parse("O=Regulator,L=Delhi,C=IN")
                    val regulator = serviceHub.identityService.wellKnownPartyFromX500Name(x500Name)
                    "This must be an Trade transaction." using (output is TradeState)
                    "This regulator cannot be initiating party." using (regulator != trade.initiatingParty)
                    "This regulator cannot be initiating party." using (regulator != trade.counterParty)
                }
            }
            val txId = subFlow(signTransactionFlow).id
            return subFlow(ReceiveFinalityFlow(otherPartyFlow, expectedTxId = txId))
        }
    }
}
