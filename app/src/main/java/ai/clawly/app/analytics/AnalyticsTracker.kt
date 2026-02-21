package ai.clawly.app.analytics
 
interface AnalyticsTracker {
    fun track(event: String, params: Map<String, String> = emptyMap())
}