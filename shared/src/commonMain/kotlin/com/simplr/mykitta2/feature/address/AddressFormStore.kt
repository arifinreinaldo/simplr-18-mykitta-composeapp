package com.simplr.mykitta2.feature.address

import com.arkivanov.mvikotlin.core.store.Reducer
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineBootstrapper
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineExecutor
import com.simplr.mykitta2.core.error.ErrorMapper
import com.simplr.mykitta2.core.result.Outcome
import com.simplr.mykitta2.data.net.dto.AddressRequest
import com.simplr.mykitta2.data.prefs.CountryStore
import com.simplr.mykitta2.data.repo.AddressRepository
import com.simplr.mykitta2.domain.Address
import com.simplr.mykitta2.domain.Country
import kotlinx.coroutines.launch

interface AddressFormStore : Store<AddressFormStore.Intent, AddressFormStore.State, AddressFormStore.Label> {

    enum class Field {
        NAME, CONTACT, PHONE, ADDRESS1, ADDRESS2, CITY, ZIPCODE, BARANGAY, PROVINCE, SUBDIVISION,
    }

    data class FormFields(
        val name: String = "",
        val contact: String = "",
        val phone: String = "",
        val address1: String = "",
        val address2: String = "",
        val city: String = "",
        val zipcode: String = "",
        val barangay: String = "",
        val province: String = "",
        val subdivision: String = "",
    )

    sealed interface Mode {
        data object Create : Mode
        data class Edit(val customerAddressId: String) : Mode
    }

    data class State(
        val mode: Mode = Mode.Create,
        val country: Country = Country.PH,
        val fields: FormFields = FormFields(),
        val errors: Map<Field, String> = emptyMap(),
        val initialLoading: Boolean = false,
        val submitting: Boolean = false,
        val isValid: Boolean = false,
        val error: String? = null,
    )

    sealed interface Intent {
        data class FieldChanged(val field: Field, val value: String) : Intent
        data object Submit : Intent
        data object DismissError : Intent
    }

    sealed interface Label {
        data object Saved : Label
    }
}

/** Caller-supplied parameters — captured from the navigation route
 *  (Destination.AddressForm). Null id = create; non-null = edit-prefill. */
data class AddressFormArgs(val customerAddressId: String?)

class AddressFormStoreFactory(
    private val storeFactory: StoreFactory,
    private val repository: AddressRepository,
    private val countryStore: CountryStore,
    private val args: AddressFormArgs,
) {
    fun create(): AddressFormStore =
        object : AddressFormStore,
            Store<AddressFormStore.Intent, AddressFormStore.State, AddressFormStore.Label>
            by storeFactory.create(
                name = "AddressFormStore",
                initialState = initialState(),
                bootstrapper = BootstrapperImpl(),
                executorFactory = { ExecutorImpl() },
                reducer = ReducerImpl,
            ) {}

    private fun initialState(): AddressFormStore.State {
        val mode = args.customerAddressId
            ?.takeIf { it.isNotEmpty() }
            ?.let { AddressFormStore.Mode.Edit(it) }
            ?: AddressFormStore.Mode.Create
        return AddressFormStore.State(
            mode = mode,
            initialLoading = mode is AddressFormStore.Mode.Edit,
        )
    }

    private sealed interface Action {
        data object Bootstrap : Action
    }

    private sealed interface Message {
        data class CountryResolved(val country: Country) : Message
        data class Prefilled(val fields: AddressFormStore.FormFields) : Message
        data object PrefillFinished : Message
        data class FieldEdited(val field: AddressFormStore.Field, val value: String) : Message
        data class ValidationChanged(
            val errors: Map<AddressFormStore.Field, String>,
            val isValid: Boolean,
        ) : Message
        data object SubmitStarted : Message
        data object SubmitFinished : Message
        data class ErrorSet(val error: String?) : Message
    }

    private inner class BootstrapperImpl : CoroutineBootstrapper<Action>() {
        override fun invoke() {
            dispatch(Action.Bootstrap)
        }
    }

    private inner class ExecutorImpl :
        CoroutineExecutor<AddressFormStore.Intent, Action, AddressFormStore.State, Message, AddressFormStore.Label>() {

        override fun executeAction(action: Action) {
            when (action) {
                Action.Bootstrap -> scope.launch {
                    val country = countryStore.read() ?: Country.PH
                    dispatch(Message.CountryResolved(country))
                    val mode = state().mode
                    if (mode is AddressFormStore.Mode.Edit) {
                        val cached = repository.findById(mode.customerAddressId)
                        if (cached != null) {
                            dispatch(Message.Prefilled(cached.toFormFields()))
                        }
                        dispatch(Message.PrefillFinished)
                    }
                    revalidate()
                }
            }
        }

        override fun executeIntent(intent: AddressFormStore.Intent) {
            when (intent) {
                is AddressFormStore.Intent.FieldChanged -> {
                    dispatch(Message.FieldEdited(intent.field, intent.value))
                    revalidate()
                }
                AddressFormStore.Intent.DismissError -> dispatch(Message.ErrorSet(null))
                AddressFormStore.Intent.Submit -> submit()
            }
        }

        private fun submit() {
            val s = state()
            if (s.submitting) return
            // Re-run validation defensively in case Submit fired before the
            // last FieldChanged debounced through.
            val (errors, valid) = validate(s.fields, s.country)
            if (!valid) {
                dispatch(Message.ValidationChanged(errors, valid))
                return
            }
            // The local "__primary__" id is our synthetic stand-in for a wire
            // row that came back with a blank CustomerAddressID. Translate
            // back when sending — the backend's anchor-row sentinel is empty
            // string, not our local key. Real ids (server-assigned, or the
            // literal "Default") pass through unchanged.
            val customerAddressId = (s.mode as? AddressFormStore.Mode.Edit)
                ?.customerAddressId
                ?.takeIf { !it.startsWith(SYNTHETIC_PRIMARY_PREFIX) }
                .orEmpty()
            val request = AddressRequest(
                customerAddressId = customerAddressId,
                name = s.fields.name.trim(),
                address1 = s.fields.address1.trim(),
                address2 = s.fields.address2.trim(),
                zipcode = s.fields.zipcode.trim(),
                city = s.fields.city.trim(),
                phone = s.fields.phone.trim(),
                contact = s.fields.contact.trim(),
                barangay = s.fields.barangay.trim(),
                province = s.fields.province.trim(),
                subdivision = s.fields.subdivision.trim(),
            )
            dispatch(Message.SubmitStarted)
            scope.launch {
                when (val outcome = repository.save(request)) {
                    is Outcome.Success -> {
                        dispatch(Message.SubmitFinished)
                        publish(AddressFormStore.Label.Saved)
                    }
                    is Outcome.Failure -> {
                        dispatch(Message.SubmitFinished)
                        dispatch(Message.ErrorSet(ErrorMapper.message(outcome.error)))
                    }
                    Outcome.Idle, Outcome.Loading -> dispatch(Message.SubmitFinished)
                }
            }
        }

        private fun revalidate() {
            val s = state()
            val (errors, valid) = validate(s.fields, s.country)
            dispatch(Message.ValidationChanged(errors, valid))
        }
    }

    private object ReducerImpl : Reducer<AddressFormStore.State, Message> {
        override fun AddressFormStore.State.reduce(msg: Message): AddressFormStore.State = when (msg) {
            is Message.CountryResolved -> copy(country = msg.country)
            is Message.Prefilled -> copy(fields = msg.fields)
            Message.PrefillFinished -> copy(initialLoading = false)
            is Message.FieldEdited -> copy(fields = fields.set(msg.field, msg.value), error = null)
            is Message.ValidationChanged -> copy(errors = msg.errors, isValid = msg.isValid)
            Message.SubmitStarted -> copy(submitting = true, error = null)
            Message.SubmitFinished -> copy(submitting = false)
            is Message.ErrorSet -> copy(error = msg.error)
        }
    }

    private companion object {
        /**
         * Local-cache prefix for rows whose wire `CustomerAddressID` was blank.
         * Editing one sends `customerAddressId = ""` to `User/AddAddress` so
         * the backend updates the anchor row rather than creating a new one.
         * Kept in sync with `DefaultAddressRepository.PRIMARY_KEY_PLACEHOLDER`.
         */
        const val SYNTHETIC_PRIMARY_PREFIX = "__primary__"
    }
}

// ---- Helpers (shared between executor + tests) ------------------------------

internal fun AddressFormStore.FormFields.set(
    field: AddressFormStore.Field,
    value: String,
): AddressFormStore.FormFields = when (field) {
    AddressFormStore.Field.NAME -> copy(name = value)
    AddressFormStore.Field.CONTACT -> copy(contact = value)
    AddressFormStore.Field.PHONE -> copy(phone = value)
    AddressFormStore.Field.ADDRESS1 -> copy(address1 = value)
    AddressFormStore.Field.ADDRESS2 -> copy(address2 = value)
    AddressFormStore.Field.CITY -> copy(city = value)
    AddressFormStore.Field.ZIPCODE -> copy(zipcode = value)
    AddressFormStore.Field.BARANGAY -> copy(barangay = value)
    AddressFormStore.Field.PROVINCE -> copy(province = value)
    AddressFormStore.Field.SUBDIVISION -> copy(subdivision = value)
}

internal fun Address.toFormFields() = AddressFormStore.FormFields(
    name = name,
    contact = contact,
    phone = phone,
    address1 = address1,
    address2 = address2,
    city = city,
    zipcode = zipcode,
    barangay = barangay,
    province = province,
    subdivision = subdivision,
)

/**
 * Per-field validation. Country-aware: PH users must additionally fill
 * [Field.PROVINCE] and [Field.BARANGAY]. Phone is digits-only (≥7) — the
 * address phone is "delivery phone" not "login phone", so country dial-code
 * normalisation is out of scope here.
 *
 * Returns the per-field error map plus a top-level isValid flag.
 */
internal fun validate(
    fields: AddressFormStore.FormFields,
    country: Country,
): Pair<Map<AddressFormStore.Field, String>, Boolean> {
    val errors = mutableMapOf<AddressFormStore.Field, String>()
    fun require(field: AddressFormStore.Field, value: String) {
        if (value.trim().isEmpty()) errors[field] = "Required"
    }
    require(AddressFormStore.Field.NAME, fields.name)
    require(AddressFormStore.Field.CONTACT, fields.contact)
    require(AddressFormStore.Field.PHONE, fields.phone)
    require(AddressFormStore.Field.ADDRESS1, fields.address1)
    require(AddressFormStore.Field.CITY, fields.city)
    require(AddressFormStore.Field.ZIPCODE, fields.zipcode)
    val phoneTrim = fields.phone.trim()
    if (phoneTrim.isNotEmpty()) {
        when {
            !phoneTrim.all { it.isDigit() } -> errors[AddressFormStore.Field.PHONE] = "Digits only"
            phoneTrim.length < PHONE_MIN_DIGITS -> errors[AddressFormStore.Field.PHONE] = "Too short"
        }
    }
    if (country == Country.PH) {
        require(AddressFormStore.Field.PROVINCE, fields.province)
        require(AddressFormStore.Field.BARANGAY, fields.barangay)
    }
    return errors to errors.isEmpty()
}

private const val PHONE_MIN_DIGITS = 7
