package ai.clawly.app.data.service

import android.app.Activity
import android.util.Log
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Offerings
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.interfaces.GetStoreProductsCallback
import com.revenuecat.purchases.interfaces.PurchaseCallback
import com.revenuecat.purchases.interfaces.ReceiveCustomerInfoCallback
import com.revenuecat.purchases.interfaces.ReceiveOfferingsCallback
import com.revenuecat.purchases.models.StoreTransaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

private const val TAG = "PurchaseService"
private const val ENTITLEMENT_ID = "pro" // Your entitlement ID in RevenueCat

data class SubscriptionStatus(
    val isActive: Boolean,
    val expirationDate: Long? = null,
    val willRenew: Boolean = false
)

data class ProductInfo(
    val packageId: String,
    val productId: String,
    val price: String,
    val priceAmountMicros: Long,
    val title: String,
    val description: String,
    val subscriptionPeriod: String?,
    val isMonthly: Boolean
)

@Singleton
class PurchaseService @Inject constructor() {

    private val _subscriptionStatus = MutableStateFlow(SubscriptionStatus(isActive = false))
    val subscriptionStatus: StateFlow<SubscriptionStatus> = _subscriptionStatus.asStateFlow()

    private val _offerings = MutableStateFlow<List<ProductInfo>>(emptyList())
    val offerings: StateFlow<List<ProductInfo>> = _offerings.asStateFlow()

    private var currentOfferings: Offerings? = null

    init {
        checkSubscriptionStatus()
    }

    fun checkSubscriptionStatus() {
        Purchases.sharedInstance.getCustomerInfo(object : ReceiveCustomerInfoCallback {
            override fun onReceived(customerInfo: CustomerInfo) {
                updateSubscriptionStatus(customerInfo)
            }

            override fun onError(error: PurchasesError) {
                Log.e(TAG, "Error fetching customer info: ${error.message}")
            }
        })
    }

    private fun updateSubscriptionStatus(customerInfo: CustomerInfo) {
        val entitlement = customerInfo.entitlements[ENTITLEMENT_ID]
        val isActive = entitlement?.isActive == true

        _subscriptionStatus.value = SubscriptionStatus(
            isActive = isActive,
            expirationDate = entitlement?.expirationDate?.time,
            willRenew = entitlement?.willRenew == true
        )

        Log.d(TAG, "Subscription status: isActive=$isActive")
    }

    suspend fun fetchOfferings(): Result<List<ProductInfo>> = suspendCancellableCoroutine { cont ->
        Purchases.sharedInstance.getOfferings(object : ReceiveOfferingsCallback {
            override fun onReceived(offerings: Offerings) {
                currentOfferings = offerings
                val current = offerings.current
                if (current == null) {
                    Log.w(TAG, "No current offering available")
                    cont.resume(Result.success(emptyList()))
                    return
                }

                val products = current.availablePackages.mapNotNull { pkg ->
                    try {
                        val product = pkg.product
                        val price = product.price
                        val subscriptionPeriod = product.period?.iso8601?.uppercase()
                        val isMonthly = when (subscriptionPeriod) {
                            "P1M" -> true
                            "P1Y" -> false
                            else -> pkg.identifier.contains("month", ignoreCase = true) ||
                                    product.id.contains("month", ignoreCase = true)
                        }

                        ProductInfo(
                            packageId = pkg.identifier,
                            productId = product.id,
                            price = price.formatted,
                            priceAmountMicros = price.amountMicros,
                            title = product.title,
                            description = product.description,
                            subscriptionPeriod = subscriptionPeriod,
                            isMonthly = isMonthly
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing package: ${e.message}")
                        null
                    }
                }

                _offerings.value = products
                Log.d(TAG, "Fetched ${products.size} products")
                cont.resume(Result.success(products))
            }

            override fun onError(error: PurchasesError) {
                Log.e(TAG, "Error fetching offerings: ${error.message}")
                cont.resume(Result.failure(Exception(error.message)))
            }
        })
    }

    suspend fun purchase(activity: Activity, packageId: String): Result<SubscriptionStatus> =
        suspendCancellableCoroutine { cont ->
            val offerings = currentOfferings?.current
            if (offerings == null) {
                cont.resume(Result.failure(Exception("No offerings available")))
                return@suspendCancellableCoroutine
            }

            val pkg = offerings.availablePackages.find { it.identifier == packageId }
            if (pkg == null) {
                cont.resume(Result.failure(Exception("Package not found: $packageId")))
                return@suspendCancellableCoroutine
            }

            Purchases.sharedInstance.purchase(
                PurchaseParams.Builder(activity, pkg).build(),
                object : PurchaseCallback {
                    override fun onCompleted(storeTransaction: StoreTransaction, customerInfo: CustomerInfo) {
                        updateSubscriptionStatus(customerInfo)
                        Log.d(TAG, "Purchase successful")
                        cont.resume(Result.success(_subscriptionStatus.value))
                    }

                    override fun onError(error: PurchasesError, userCancelled: Boolean) {
                        if (userCancelled) {
                            Log.d(TAG, "Purchase cancelled by user")
                            cont.resume(Result.failure(Exception("Purchase cancelled")))
                        } else {
                            Log.e(TAG, "Purchase error: ${error.message}")
                            cont.resume(Result.failure(Exception(error.message)))
                        }
                    }
                }
            )
        }

    suspend fun restorePurchases(): Result<SubscriptionStatus> = suspendCancellableCoroutine { cont ->
        Purchases.sharedInstance.restorePurchases(object : ReceiveCustomerInfoCallback {
            override fun onReceived(customerInfo: CustomerInfo) {
                updateSubscriptionStatus(customerInfo)
                Log.d(TAG, "Restore successful, isActive=${_subscriptionStatus.value.isActive}")
                cont.resume(Result.success(_subscriptionStatus.value))
            }

            override fun onError(error: PurchasesError) {
                Log.e(TAG, "Restore error: ${error.message}")
                cont.resume(Result.failure(Exception(error.message)))
            }
        })
    }

    fun getPackageById(packageId: String): Package? {
        return currentOfferings?.current?.availablePackages?.find { it.identifier == packageId }
    }
}
