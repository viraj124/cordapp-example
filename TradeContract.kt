package com.example.contract

import com.example.state.TradeState
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction

/**
 * A implementation of a basic smart contract in Corda.
 *
 * This contract enforces rules regarding the creation of a valid [TradeState], which in turn encapsulates an [TradeState].
 *
 * For a new [TradeState] to be issued onto the ledger, a transaction is required which takes:
 * - Zero input states.
 * - One output state: the new [TradeState].
 * - An Create() command with the public keys of both the lender and the borrower.
 *
 * All contracts must sub-class the [Contract] interface.
 */
class TradeContract : Contract {
    companion object {
        @JvmStatic
        val ID = "com.example.contract.TradeContract"
    }

    /**
     * The verify() function of all the states' contracts must not throw an exception for a transaction to be
     * considered valid.
     */
    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Commands>()
        when (command.value) {
            is Commands.Create -> {
                requireThat {
                    // Generic constraints around the Trade transaction.
                    "No inputs should be consumed when issuing an Trade." using (tx.inputs.isEmpty())
                    "Only one output state should be created." using (tx.outputs.size == 1)
                    val out = tx.outputsOfType<TradeState>().single()
                    val expectedSigners = listOf(out.initiatingParty.owningKey,out.counterParty.owningKey)
                    "The creating party and the counter party cannot be the same entity." using (out.initiatingParty != out.counterParty)
                    "All of the participants must be signers." using (command.signers.containsAll(expectedSigners))

                    // Trade-specific constraints.
                    "The Trade's sell value must be non-negative." using (out.transactionAmount > 0)
                    "The Trade's buy value must be non-negative." using (out.transactionFees > 0)
                    "The Trade's buy value must be non-negative." using (out.transactionFees > 0)
                    "The Trade's sell currency can't be empty." using (out.assetCode.isNotEmpty())
                }
            }
//            is Commands.CounterTrade -> {
//                requireThat {
//                    // Generic constraints around the Trade transaction.
//                    "Only one output state should be created." using (tx.outputs.size == 1)
//                    val out = tx.outputsOfType<TradeState>().single()
//                    val expectedSigners = listOf(out.initiatingParty.owningKey,out.counterParty.owningKey)
//                    "The creating party and the counter party cannot be the same entity." using (out.initiatingParty != out.counterParty)
//                    "All of the participants must be signers." using (command.signers.containsAll(expectedSigners))
//
//                    // Trade-specific constraints.
//                    "The sell currency and the buy currency cannot be the same entity." using (out.sellCurrency != out.buyCurrency)
//                    "The Trade's sell value must be non-negative." using (out.sellValue > 0)
//                    "The Trade's buy value must be non-negative." using (out.buyValue > 0)
//                    "The Trade's sell currency can't be empty." using (out.sellCurrency.isNotEmpty())
//                    "The Trade's buy currency can't be empty." using (out.buyCurrency.isNotEmpty())
//                }
//            }
        }
    }

    /**
     * This contract only implements one command, Create.
     */
    interface Commands : CommandData {
        class Create : Commands
        class CounterTrade : Commands
    }
}
