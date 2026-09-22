package com.example.data.remote

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class FirebaseDiagnosticResult(
    val isSuccess: Boolean,
    val projectId: String,
    val message: String,
    val recommendation: String? = null
)

/**
 * Classe di configurazione per gestire l'inizializzazione e l'accesso all'istanza di Cloud Firestore.
 */
object FirebaseHelper {
    private const val TAG = "FirebaseHelper"

    @Volatile
    private var firestoreInstance: FirebaseFirestore? = null

    /**
     * Inizializza Firebase se non già attivo e restituisce l'istanza di Firestore.
     */
    fun initialize(context: Context? = null): FirebaseFirestore? {
        return try {
            if (context != null && FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }
            getFirestore()
        } catch (e: Exception) {
            Log.e(TAG, "Errore durante l'inizializzazione di Firebase Firestore", e)
            null
        }
    }

    /**
     * Restituisce l'istanza singleton di FirebaseFirestore configurata con persistenza locale abilitata.
     */
    fun getFirestore(): FirebaseFirestore {
        return firestoreInstance ?: synchronized(this) {
            firestoreInstance ?: try {
                val db = FirebaseFirestore.getInstance()
                val settings = FirebaseFirestoreSettings.Builder()
                    .setPersistenceEnabled(true)
                    .build()
                db.firestoreSettings = settings
                firestoreInstance = db
                db
            } catch (e: Exception) {
                Log.e(TAG, "Impossibile ottenere l'istanza di Firestore: ${e.message}")
                FirebaseFirestore.getInstance().also { firestoreInstance = it }
            }
        }
    }

    /**
     * Restituisce l'ID del progetto Firebase corrente.
     */
    fun getProjectId(): String {
        return try {
            FirebaseApp.getInstance().options.projectId ?: "Non specificato"
        } catch (e: Exception) {
            "Non inizializzato"
        }
    }

    /**
     * Formatta un errore Firestore in un messaggio diagnostico chiaro in italiano con consigli di risoluzione.
     */
    fun formatFirestoreError(e: Throwable): String {
        val msg = e.message.orEmpty()
        val causeMsg = e.cause?.message.orEmpty()
        val combined = "$msg $causeMsg"

        return when {
            combined.contains("PERMISSION_DENIED", ignoreCase = true) ->
                "PERMESSI NEGATI (PERMISSION_DENIED): Le regole di sicurezza di Firestore bloccano la lettura/scrittura. Su Firebase Console > Firestore Database > Regole, imposta: 'allow read, write: if true;'"

            combined.contains("UNAVAILABLE", ignoreCase = true) || combined.contains("UNREACHABLE", ignoreCase = true) ->
                "SERVER NON RAGGIUNGIBILE (UNAVAILABLE): Impossibile contattare il database del progetto '${getProjectId()}'. Verifica che il database Firestore sia stato creato nella Firebase Console e che la connessione internet sia attiva."

            combined.contains("API_KEY_INVALID", ignoreCase = true) || combined.contains("AIzaSyDummy", ignoreCase = true) || combined.contains("API key not valid", ignoreCase = true) ->
                "CHIAVE API NON VALIDA: È presente una chiave API predefinita o non valida in google-services.json. Scarica il file google-services.json autentico dalla tua Firebase Console."

            combined.contains("NOT_FOUND", ignoreCase = true) ->
                "DATABASE NON TROVATO (NOT_FOUND): Il database Cloud Firestore non è ancora stato creato nel progetto '${getProjectId()}'. Vai su Firebase Console e clicca su 'Crea database'."

            e is java.util.concurrent.TimeoutException || combined.contains("Timeout", ignoreCase = true) ->
                "TIMEOUT DI RETE (10s): Il server Firestore non ha risposto in tempo. Verifica che il progetto Firebase '${getProjectId()}' esista su Google Cloud e che il dispositivo sia connesso a Internet."

            else ->
                "ERRORE FIRESTORE: ${e.localizedMessage ?: e.javaClass.simpleName}"
        }
    }

    /**
     * Esegue un test diagnostico di connessione a Firestore.
     */
    suspend fun testConnection(): FirebaseDiagnosticResult = withContext(Dispatchers.IO) {
        val projectId = getProjectId()
        try {
            val db = getFirestore()
            // Tenta di leggere o scrivere un documento di ping di test con timeout di 8 secondi
            val pingDoc = db.collection("_diagnostics").document("ping")
            val pingData = hashMapOf<String, Any>(
                "timestamp" to System.currentTimeMillis(),
                "client" to "ArchivioLibri Diagnostic"
            )

            val success = withTimeoutOrNull(8000L) {
                try {
                    pingDoc.set(pingData).await()
                    true
                } catch (e: Exception) {
                    throw e
                }
            }

            if (success == true) {
                FirebaseDiagnosticResult(
                    isSuccess = true,
                    projectId = projectId,
                    message = "Connessione a Firestore riuscita! Il database remoto è pronto per l'esportazione e sincronizzazione."
                )
            } else {
                FirebaseDiagnosticResult(
                    isSuccess = false,
                    projectId = projectId,
                    message = "Timeout durante il test di connessione (8s).",
                    recommendation = "Verifica la connessione internet e che il progetto '$projectId' esista nella Google Firebase Console con Firestore Database attivo."
                )
            }
        } catch (e: Exception) {
            val formatted = formatFirestoreError(e)
            val recommendation = when {
                formatted.contains("PERMISSION_DENIED") -> "Vai su Firebase Console > Firestore Database > Regole (Rules) e consenti read/write."
                formatted.contains("CHIAVE API") -> "Sostituisci google-services.json con quello autentico generato su console.firebase.google.com per il tuo progetto."
                else -> "Assicurati di aver creato un database Firestore in modalità 'Test' nel tuo progetto Google Firebase."
            }
            FirebaseDiagnosticResult(
                isSuccess = false,
                projectId = projectId,
                message = formatted,
                recommendation = recommendation
            )
        }
    }

    /**
     * Verifica se Firebase è correttamente inizializzato nell'ambiente.
     */
    fun isInitialized(): Boolean {
        return try {
            FirebaseApp.getApps(FirebaseApp.getInstance().applicationContext).isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }
}
