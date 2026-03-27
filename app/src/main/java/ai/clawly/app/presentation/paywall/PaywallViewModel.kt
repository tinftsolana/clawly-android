package ai.clawly.app.presentation.paywall

import android.app.Activity
import android.util.Log
import ai.clawly.app.data.remote.ControlPlaneService
import ai.clawly.app.data.remote.RemoteConfigFlags
import ai.clawly.app.data.remote.gateway.DeviceIdentityManager
import ai.clawly.app.data.service.ProductInfo
import ai.clawly.app.data.service.PurchaseService
import ai.clawly.app.domain.usecase.Web2CreditsUseCase
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "PaywallViewModel"

enum class SubscriptionType {
    Monthly, Yearly
}

data class SubscriptionProduct(
    val id: String,
    val packageId: String,
    val type: SubscriptionType,
    val price: Double,
    val localizedPrice: String,
    val title: String,
    val description: String
)

data class CreditPack(
    val id: String,
    val creditsAmount: Int,
    val priceLabel: String
)

data class PaywallUiState(
    val products: List<SubscriptionProduct> = emptyList(),
    val isLoading: Boolean = false,
    val isPurchasing: Boolean = false,
    val isRestoring: Boolean = false,
    val selectedPlan: PlanType = PlanType.Yearly,
    val purchaseCompleted: Boolean = false,
    val showError: Boolean = false,
    val error: String? = null,
    val monthlyPrice: String = "$9.99",
    val yearlyPrice: String = "$49.99",
    // Credit packs
    val creditPacks: List<CreditPack> = emptyList(),
    val selectedCreditPackId: String? = null,
    val isPurchasingCredits: Boolean = false,
    val creditPurchaseSuccess: Boolean = false,
    val currentCreditsDisplay: String = ""
)

@HiltViewModel
class PaywallViewModel @Inject constructor(
    private val purchaseService: PurchaseService,
    private val controlPlaneService: ControlPlaneService,
    private val deviceIdentityManager: DeviceIdentityManager,
    private val web2CreditsUseCase: Web2CreditsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(PaywallUiState())
    val uiState: StateFlow<PaywallUiState> = _uiState.asStateFlow()

    init {
        loadProducts()
        loadCreditPacks()
        observeSubscriptionStatus()
        observeCredits()
    }

    private fun observeCredits() {
        viewModelScope.launch {
            web2CreditsUseCase.displayStringFlow.collect { display ->
                _uiState.update { it.copy(currentCreditsDisplay = display) }
            }
        }
    }

    private fun observeSubscriptionStatus() {
        viewModelScope.launch {
            purchaseService.subscriptionStatus.collect { status ->
                if (status.isActive) {
                    _uiState.update { it.copy(purchaseCompleted = true) }
                }
            }
        }
    }

    fun loadProducts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            purchaseService.fetchOfferings()
                .onSuccess { products ->
                    val subscriptionProducts = products.map { productInfo ->
                        mapToSubscriptionProduct(productInfo)
                    }

                    val monthlyPrice = subscriptionProducts
                        .find { it.type == SubscriptionType.Monthly }
                        ?.localizedPrice ?: "$9.99"

                    val yearlyPrice = subscriptionProducts
                        .find { it.type == SubscriptionType.Yearly }
                        ?.localizedPrice ?: "$49.99"

                    _uiState.update {
                        it.copy(
                            products = subscriptionProducts,
                            isLoading = false,
                            monthlyPrice = monthlyPrice,
                            yearlyPrice = yearlyPrice
                        )
                    }

                    Log.d(TAG, "Loaded ${subscriptionProducts.size} products: monthly=$monthlyPrice, yearly=$yearlyPrice")
                }
                .onFailure { error ->
                    Log.e(TAG, "Failed to load products", error)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "Failed to load products",
                            showError = true
                        )
                    }
                }
        }
    }

    private fun mapToSubscriptionProduct(productInfo: ProductInfo): SubscriptionProduct {
        val subscriptionType = when (productInfo.subscriptionPeriod?.uppercase()) {
            "P1M" -> SubscriptionType.Monthly
            "P1Y" -> SubscriptionType.Yearly
            else -> if (productInfo.isMonthly) SubscriptionType.Monthly else SubscriptionType.Yearly
        }

        return SubscriptionProduct(
            id = productInfo.productId,
            packageId = productInfo.packageId,
            type = subscriptionType,
            price = productInfo.priceAmountMicros / 1_000_000.0,
            localizedPrice = productInfo.price,
            title = productInfo.title,
            description = productInfo.description
        )
    }

    val monthlySubscription: SubscriptionProduct?
        get() = _uiState.value.products.find { it.type == SubscriptionType.Monthly }

    val yearlySubscription: SubscriptionProduct?
        get() = _uiState.value.products.find { it.type == SubscriptionType.Yearly }

    val selectedProduct: SubscriptionProduct?
        get() = when (_uiState.value.selectedPlan) {
            PlanType.Monthly -> monthlySubscription
            PlanType.Yearly -> yearlySubscription
        }

    val yearlySavingsPercent: Int?
        get() {
            val monthly = monthlySubscription ?: return null
            val yearly = yearlySubscription ?: return null

            val monthlyAnnualized = monthly.price * 12
            if (monthlyAnnualized <= 0) return null

            val savings = (1 - (yearly.price / monthlyAnnualized)) * 100
            return savings.toInt()
        }

    fun selectPlan(plan: PlanType) {
        _uiState.update { it.copy(selectedPlan = plan) }
    }

    fun purchaseSelectedProduct(activity: Activity) {
        val product = selectedProduct ?: run {
            _uiState.update {
                it.copy(
                    error = "Product not available",
                    showError = true
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isPurchasing = true) }

            purchaseService.purchase(activity, product.packageId)
                .onSuccess { status ->
                    Log.d(TAG, "Purchase successful: isActive=${status.isActive}")
                    if (status.isActive) {
                        syncPurchasesToBackend()
                    }
                    _uiState.update {
                        it.copy(
                            isPurchasing = false,
                            purchaseCompleted = status.isActive
                        )
                    }
                }
                .onFailure { error ->
                    Log.e(TAG, "Purchase failed", error)
                    val isCancelled = error.message?.contains("cancelled", ignoreCase = true) == true
                    _uiState.update {
                        it.copy(
                            isPurchasing = false,
                            error = if (isCancelled) null else error.message,
                            showError = !isCancelled
                        )
                    }
                }
        }
    }

    fun restorePurchases() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRestoring = true) }

            purchaseService.restorePurchases()
                .onSuccess { status ->
                    Log.d(TAG, "Restore successful: isActive=${status.isActive}")
                    if (status.isActive) {
                        syncPurchasesToBackend()
                    }
                    _uiState.update {
                        it.copy(
                            isRestoring = false,
                            purchaseCompleted = status.isActive,
                            error = if (!status.isActive) "No active subscription found" else null,
                            showError = !status.isActive
                        )
                    }
                }
                .onFailure { error ->
                    Log.e(TAG, "Restore failed", error)
                    _uiState.update {
                        it.copy(
                            isRestoring = false,
                            error = error.message ?: "Restore failed",
                            showError = true
                        )
                    }
                }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(showError = false, error = null) }
    }

    private fun syncPurchasesToBackend() {
        viewModelScope.launch {
            val userId = resolveSyncUserId()
            if (userId.isNullOrEmpty()) {
                Log.w(TAG, "syncPurchases skipped: no userId available")
                return@launch
            }
            controlPlaneService.syncPurchases(userId).fold(
                onSuccess = { credits ->
                    Log.d(TAG, "syncPurchases success: credits=$credits")
                    web2CreditsUseCase.setCredits(credits)
                },
                onFailure = { e ->
                    Log.e(TAG, "syncPurchases failed", e)
                }
            )
        }
    }

    private suspend fun resolveSyncUserId(): String? {
        return deviceIdentityManager.loadOrCreateIdentity()?.deviceId
    }

    // --- Credit Packs ---

    private fun loadCreditPacks() {
        val packConfigs = RemoteConfigFlags.getCreditPacks()
        if (packConfigs.isEmpty()) {
            Log.d(TAG, "No credit packs in RemoteConfig")
            return
        }

        val packs = packConfigs
            .sortedBy { it.creditsAmount }
            .map { CreditPack(id = it.packId, creditsAmount = it.creditsAmount, priceLabel = it.priceLabel ?: "...") }

        _uiState.update {
            it.copy(
                creditPacks = packs,
                selectedCreditPackId = packs.firstOrNull()?.id
            )
        }
        Log.d(TAG, "Loaded ${packs.size} credit packs from RemoteConfig")
    }

    fun selectCreditPack(packId: String) {
        _uiState.update { it.copy(selectedCreditPackId = packId) }
    }

    fun purchaseCreditPack(activity: Activity) {
        val pack = _uiState.value.creditPacks.find { it.id == _uiState.value.selectedCreditPackId }
        if (pack == null) {
            _uiState.update { it.copy(error = "No credit pack selected", showError = true) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isPurchasingCredits = true) }

            purchaseService.purchaseConsumableByProductId(activity, pack.id)
                .onSuccess {
                    Log.d(TAG, "Credit pack purchase successful: ${pack.id}")
                    _uiState.update { it.copy(isPurchasingCredits = false, creditPurchaseSuccess = true) }
                    // Sync with backend to get updated credit balance
                    syncPurchasesToBackend()
                }
                .onFailure { error ->
                    Log.e(TAG, "Credit pack purchase failed", error)
                    val isCancelled = error.message?.contains("cancelled", ignoreCase = true) == true
                    _uiState.update {
                        it.copy(
                            isPurchasingCredits = false,
                            error = if (isCancelled) null else error.message,
                            showError = !isCancelled
                        )
                    }
                }
        }
    }

    fun resetCreditPurchaseSuccess() {
        _uiState.update { it.copy(creditPurchaseSuccess = false) }
    }
}
