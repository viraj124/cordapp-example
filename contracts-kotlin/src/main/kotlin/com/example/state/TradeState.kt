package com.example.state

import com.example.contract.TradeContract
import com.example.schema.TradeSchemaV1
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState

/**
 * The state object recording IOU agreements between two parties.
 *
 * A state must implement [ContractState] or one of its descendants.
 *
 * @param value the value of the IOU.
 * @param lender the party issuing the IOU.
 * @param borrower the party receiving and approving the IOU.
 */
@BelongsToContract(TradeContract::class)
data class TradeState(val sellValue: Int,
                      val sellCurrency: String,
                      val buyValue: Int,
                      val buyCurrency: String,
                      val initiatingParty: Party,
                      val counterParty: Party,
                      val tradeStatus: String,
                      val regulator: Party,
                      val userId: String,
                      var assetCode: String,
                      var orderType: String,
                      var transactionAmount: Int,
                      var transactionFees: Int,
                      var transactionUnits: Int,
                      override val linearId: UniqueIdentifier = UniqueIdentifier()):

        LinearState, QueryableState {
    /** The public keys of the involved parties. */
    override val participants: List<AbstractParty> get() = listOf(initiatingParty, counterParty, regulator)

    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        return when (schema) {
            is TradeSchemaV1 -> TradeSchemaV1.PersistentTrade(
                    this.initiatingParty.name.toString(),
                    this.counterParty.name.toString(),
                    this.sellValue,
                    this.sellCurrency.toString(),
                    this.buyValue,
                    this.buyCurrency.toString(),
                    this.tradeStatus,
                    this.regulator.name.toString(),
                    this.userId,
                    this.assetCode,
                    this.orderType,
                    this.transactionAmount,
                    this.transactionFees,
                    this.transactionUnits,
                    this.linearId.id)
            else -> throw IllegalArgumentException("Unrecognised schema $schema")
        }
    }
    override fun supportedSchemas(): Iterable<MappedSchema> = listOf(TradeSchemaV1)
}

