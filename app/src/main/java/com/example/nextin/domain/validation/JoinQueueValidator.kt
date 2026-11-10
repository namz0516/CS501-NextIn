package com.example.nextin.domain.validation

import com.example.nextin.data.model.Business
import com.example.nextin.data.model.BusinessType

object JoinQueueValidator {

    fun validate(
        business: Business,
        name: String,
        partySize: Int,
        hasActiveEntry: Boolean = false
    ): String? {
        return when {
            hasActiveEntry ->
                "You already have an active queue. Leave it before joining another."

            !business.queueOpen ->
                "This queue is closed."

            name.isBlank() ->
                "Please enter your name."

            partySize < 1 ->
                "Party size must be at least 1."

            business.type == BusinessType.OTHER && partySize != 1 ->
                "This business accepts individual customers."

            business.type == BusinessType.RESTAURANT &&
                    partySize > business.settings.maxPartySize ->
                "This business accepts parties of up to ${business.settings.maxPartySize}."

            business.type == BusinessType.RESTAURANT &&
                    business.tables.none { it.capacity >= partySize } ->
                "No table can accommodate this party size."

            else -> null
        }
    }
}