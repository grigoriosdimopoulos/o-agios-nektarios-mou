package gr.agiosnektarios.village.ui.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import gr.agiosnektarios.village.core.model.ContactKind
import gr.agiosnektarios.village.core.model.VillageContact
import gr.agiosnektarios.village.data.contact.ContactRepository
import gr.agiosnektarios.village.data.session.SessionRepository
import gr.agiosnektarios.village.data.session.SessionState
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ContactsUiState(
    val local: List<VillageContact> = emptyList(),
    val canEdit: Boolean = false,
    val loading: Boolean = true,
)

/** The dialog's own state, kept apart so opening it does not touch the list. */
data class ContactEditorState(
    val open: Boolean = false,
    val id: String? = null,
    val name: String = "",
    val number: String = "",
    val note: String = "",
    val kind: ContactKind = ContactKind.LOCAL,
    val saving: Boolean = false,
    val invalid: Boolean = false,
) {
    val valid: Boolean get() = name.isNotBlank() && VillageContact.isDialable(number)
}

@HiltViewModel
class ContactsViewModel @Inject constructor(
    private val repository: ContactRepository,
    private val session: SessionRepository,
) : ViewModel() {

    val uiState: StateFlow<ContactsUiState> = combine(
        repository.observeContacts(),
        session.state.map { (it as? SessionState.SignedIn)?.profile?.isAdmin == true },
    ) { contacts, admin ->
        ContactsUiState(local = contacts, canEdit = admin, loading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ContactsUiState())

    private val _editor = MutableStateFlow(ContactEditorState())
    val editor: StateFlow<ContactEditorState> = _editor.asStateFlow()

    fun startAdding() {
        _editor.value = ContactEditorState(open = true)
    }

    fun startEditing(contact: VillageContact) {
        _editor.value = ContactEditorState(
            open = true,
            id = contact.id,
            name = contact.name,
            number = contact.number,
            note = contact.note,
            kind = contact.contactKind,
        )
    }

    fun dismissEditor() {
        _editor.value = ContactEditorState()
    }

    fun onName(value: String) = _editor.update { it.copy(name = value, invalid = false) }

    fun onNumber(value: String) = _editor.update { it.copy(number = value, invalid = false) }

    fun onNote(value: String) = _editor.update { it.copy(note = value) }

    fun onKind(kind: ContactKind) = _editor.update { it.copy(kind = kind) }

    fun save() {
        val current = _editor.value
        if (!current.valid) {
            _editor.update { it.copy(invalid = true) }
            return
        }
        val author = (session.state.value as? SessionState.SignedIn)?.profile ?: return
        _editor.update { it.copy(saving = true) }
        viewModelScope.launch {
            val result = repository.save(
                id = current.id,
                name = current.name,
                number = current.number,
                note = current.note,
                kind = current.kind,
                authorId = author.id,
            )
            _editor.update {
                if (result.isSuccess) ContactEditorState() else it.copy(saving = false, invalid = true)
            }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }
}
