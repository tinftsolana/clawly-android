package ai.clawly.app.presentation.chat.components

import ai.clawly.app.domain.model.SignRequestBubbleState
import ai.clawly.app.ui.theme.ClawlyColors
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun shortenAddress(address: String?): String {
    if (address.isNullOrEmpty()) return "-"
    return if (address.length > 10) "${address.take(6)}...${address.takeLast(4)}" else address
}

@Composable
fun SignRequestBubble(
    state: SignRequestBubbleState,
    onSign: () -> Unit,
    onReject: () -> Unit,
    onOpenSolscan: (signature: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(shape)
                .border(1.dp, ClawlyColors.surfaceBorder, shape)
                .background(ClawlyColors.surfaceElevated)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (state) {
                is SignRequestBubbleState.ReadyToSign -> ReadyToSignContent(state, onSign, onReject)
                is SignRequestBubbleState.Signing -> SigningContent()
                is SignRequestBubbleState.Success -> SuccessContent(state, onOpenSolscan)
                is SignRequestBubbleState.Rejected -> RejectedContent(state)
            }
        }
    }
}

@Composable
private fun ReadyToSignContent(
    state: SignRequestBubbleState.ReadyToSign,
    onSign: () -> Unit,
    onReject: () -> Unit
) {
    Text(
        text = "Sign Transaction",
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        color = ClawlyColors.textPrimary
    )

    Spacer(modifier = Modifier.height(12.dp))

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        DetailRow("From", shortenAddress(state.fromWallet))
        DetailRow("To", shortenAddress(state.toWallet))
        if (state.txHash != null) {
            DetailRow("Tx", shortenAddress(state.txHash))
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = onSign,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = ClawlyColors.accentPrimary
            ),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("Sign", fontWeight = FontWeight.SemiBold)
        }

        OutlinedButton(
            onClick = onReject,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = ClawlyColors.secondaryText
            ),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("Reject")
        }
    }
}

@Composable
private fun SigningContent() {
    Spacer(modifier = Modifier.height(8.dp))

    CircularProgressIndicator(
        modifier = Modifier.size(28.dp),
        color = ClawlyColors.accentPrimary,
        strokeWidth = 2.5.dp
    )

    Spacer(modifier = Modifier.height(12.dp))

    Text(
        text = "Signing transaction...",
        fontSize = 14.sp,
        color = ClawlyColors.secondaryText
    )

    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun SuccessContent(
    state: SignRequestBubbleState.Success,
    onOpenSolscan: (String) -> Unit
) {
    Icon(
        imageVector = Icons.Default.CheckCircle,
        contentDescription = null,
        tint = ClawlyColors.terminalGreen,
        modifier = Modifier.size(36.dp)
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = "Transaction successful!",
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        color = ClawlyColors.terminalGreen
    )

    Spacer(modifier = Modifier.height(12.dp))

    Button(
        onClick = { onOpenSolscan(state.signature) },
        colors = ButtonDefaults.buttonColors(
            containerColor = ClawlyColors.accentPrimary
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Text("SOLSCAN", fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
private fun RejectedContent(state: SignRequestBubbleState.Rejected) {
    Icon(
        imageVector = Icons.Default.Close,
        contentDescription = null,
        tint = ClawlyColors.error,
        modifier = Modifier.size(36.dp)
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = state.reason,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        color = ClawlyColors.error
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row {
        Text(
            text = "$label: ",
            fontSize = 13.sp,
            color = ClawlyColors.secondaryText
        )
        Text(
            text = value,
            fontSize = 13.sp,
            color = ClawlyColors.textPrimary
        )
    }
}
