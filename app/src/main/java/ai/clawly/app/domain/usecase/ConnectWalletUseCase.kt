package ai.clawly.app.domain.usecase

import android.util.Log
import androidx.core.net.toUri
import com.solana.mobilewalletadapter.clientlib.ConnectionIdentity
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.Solana
import com.solana.mobilewalletadapter.clientlib.TransactionResult
import com.solana.publickey.SolanaPublicKey
import ai.clawly.app.activityResultSender
import ai.clawly.app.domain.model.UserWalletDetails.Connected
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import org.sol4k.Base58
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ConnectWalletUseCase"
private const val IDENTITY_URI = "https://clawly.ai"
private const val IDENTITY_NAME = "Clawly"
private const val ICON_URI = "clawly_logo.png"

@Singleton
class ConnectWalletUseCase @Inject constructor(
    private val walletConnectionUseCase: WalletConnectionUseCase
) {
    private val walletAdapter = MobileWalletAdapter(
        ConnectionIdentity(
            identityUri = IDENTITY_URI.toUri(),
            iconUri = ICON_URI.toUri(),
            identityName = IDENTITY_NAME
        )
    )

    init {
        walletAdapter.blockchain = Solana.Mainnet
    }

    suspend fun tryToRestoreAuthToken(): Boolean {
        return try {
            val wallet = walletConnectionUseCase.walletDetails.firstOrNull() as? Connected
            val authToken = wallet?.authToken

            if (!authToken.isNullOrEmpty()) {
                walletAdapter.authToken = authToken
                Log.d(TAG, "Auth token restored successfully")
                true
            } else {
                Log.d(TAG, "No auth token to restore")
                walletAdapter.authToken = null
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore auth token: ${e.message}")
            walletAdapter.authToken = null
            walletConnectionUseCase.clearConnection()
            false
        }
    }

    suspend fun signMessage(message: String): ByteArray? {
        val sender = activityResultSender ?: return null

        tryToRestoreAuthToken()

        return (walletConnectionUseCase.walletDetails.firstOrNull() as? Connected)?.let { _ ->
            val txn = walletAdapter.transact(sender) { authResult ->
                persistConnection(
                    authResult.accounts.first().publicKey,
                    authResult.accounts.first().accountLabel.orEmpty(),
                    authResult.authToken
                )

                signMessages(
                    arrayOf(message.toByteArray()),
                    arrayOf(authResult.accounts.first().publicKey)
                )
            }

            when (txn) {
                is TransactionResult.Success -> {
                    Log.d(TAG, "Message signed successfully")
                    txn.payload.signedPayloads.firstOrNull()
                }
                is TransactionResult.Failure -> {
                    Log.e(TAG, "SignMessage failed: ${txn.message}")
                    null
                }
                is TransactionResult.NoWalletFound -> {
                    Log.e(TAG, "No wallet found for signing")
                    null
                }
            }
        }
    }

    suspend fun connect(): Boolean {
        val sender = activityResultSender ?: return false

        try {
            walletAdapter.authToken = null
            Log.d(TAG, "Reset wallet adapter state before connection")
        } catch (e: Exception) {
            Log.w(TAG, "Could not reset adapter state: ${e.message}")
        }

        return when (val result = walletAdapter.connect(sender)) {
            is TransactionResult.Success -> {
                val accounts = result.authResult.accounts.firstOrNull() ?: return false
                val authToken = result.authResult.authToken
                val address = accounts.publicKey
                val accountLabel = accounts.accountLabel ?: ""

                walletAdapter.authToken = authToken

                walletConnectionUseCase.persistConnection(
                    SolanaPublicKey(bytes = address).base58(),
                    accountLabel,
                    authToken
                )

                Log.d(TAG, "Wallet connected successfully")
                true
            }
            is TransactionResult.Failure -> {
                Log.e(TAG, "Wallet connection failed: ${result.message}")
                walletConnectionUseCase.clearConnection()
                false
            }
            is TransactionResult.NoWalletFound -> {
                Log.e(TAG, "No wallet found")
                walletConnectionUseCase.clearConnection()
                false
            }
        }
    }

    suspend fun disconnect(disconnected: () -> Unit = {}) {
        val sender = activityResultSender
        val connection = walletConnectionUseCase.walletDetails.firstOrNull()

        if (connection is Connected && sender != null) {
            try {
                Log.d(TAG, "Disconnecting wallet")
                walletAdapter.disconnect(sender)
                walletAdapter.authToken = null
                walletConnectionUseCase.clearConnection()
                Log.d(TAG, "Wallet disconnected successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error during wallet disconnect: ${e.message}")
                walletAdapter.authToken = null
                walletConnectionUseCase.clearConnection()
            }
        } else {
            Log.d(TAG, "No active connection to disconnect, clearing local state")
            walletAdapter.authToken = null
            walletConnectionUseCase.clearConnection()
        }

        delay(500L)
        disconnected()
    }

    suspend fun clear(disconnected: () -> Unit = {}) {
        walletAdapter.authToken = null
        walletConnectionUseCase.clearConnection()
        delay(500L)
        disconnected()
    }

    private suspend fun persistConnection(pubKey: ByteArray, accountLabel: String, token: String) {
        walletConnectionUseCase.persistConnection(
            SolanaPublicKey(pubKey).base58(),
            accountLabel,
            token
        )
    }
}
