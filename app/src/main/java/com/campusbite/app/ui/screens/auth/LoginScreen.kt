package com.campusbite.app.ui.screens.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.campusbite.app.R
import com.campusbite.app.ui.viewmodel.AuthState
import com.campusbite.app.ui.viewmodel.AuthViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions

private const val TERMS_URL = "https://thecampusbite.vercel.app/terms-and-conditions"
private const val PRIVACY_POLICY_URL = "https://thecampusbite.vercel.app/privacy-policy"

@Composable
fun LoginScreen(
    onNavigateToStudent: () -> Unit,
    onNavigateToShopkeeper: () -> Unit,
    onNavigateToAdmin: () -> Unit,
    onNavigateToPending: () -> Unit,
    onNavigateToCompleteProfile: () -> Unit,
    onNavigateToRegister: () -> Unit = {},
    viewModel: AuthViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val authState by viewModel.authState.collectAsState()

    val googleSignInClient = remember(context) {
        val googleSignInOptions = GoogleSignInOptions.Builder(
            GoogleSignInOptions.DEFAULT_SIGN_IN
        )
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        GoogleSignIn.getClient(context, googleSignInOptions)
    }

    val googleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            return@rememberLauncherForActivityResult
        }

        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            val account = task.result
            val idToken = account.idToken

            if (!idToken.isNullOrBlank()) {
                viewModel.signInWithGoogle(idToken)
            }
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "Google sign-in failed. Please try again.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.StudentSuccess -> {
                viewModel.resetState()
                onNavigateToStudent()
            }

            is AuthState.ShopkeeperSuccess -> {
                viewModel.resetState()
                onNavigateToShopkeeper()
            }

            is AuthState.ShopkeeperPending -> {
                viewModel.resetState()
                onNavigateToPending()
            }

            is AuthState.AdminSuccess -> {
                viewModel.resetState()
                onNavigateToAdmin()
            }

            is AuthState.GoogleProfileRequired -> {
                viewModel.resetState()
                onNavigateToCompleteProfile()
            }

            else -> Unit
        }
    }

    val errorMessage = when (authState) {
        is AuthState.Error -> (authState as AuthState.Error).message
        is AuthState.Blocked -> "Your account has been blocked by admin."
        else -> null
    }

    val agreementText = buildAnnotatedString {
        append("By continuing, you agree to our ")

        pushStringAnnotation(
            tag = "terms",
            annotation = TERMS_URL
        )
        withStyle(
            style = SpanStyle(
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
                fontWeight = FontWeight.Medium
            )
        ) {
            append("Terms & Conditions")
        }
        pop()

        append(" and ")

        pushStringAnnotation(
            tag = "privacy",
            annotation = PRIVACY_POLICY_URL
        )
        withStyle(
            style = SpanStyle(
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
                fontWeight = FontWeight.Medium
            )
        ) {
            append("Privacy Policy")
        }
        pop()

        append(".")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "CampusBite",
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Bukh Mitao, Time Bachao",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(40.dp))

        OutlinedButton(
            onClick = {
                googleSignInClient.signOut().addOnCompleteListener {
                    googleLauncher.launch(googleSignInClient.signInIntent)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = authState !is AuthState.Loading
        ) {
            if (authState is AuthState.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Text(text = "Continue with Google")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        ClickableText(
            text = agreementText,
            modifier = Modifier.fillMaxWidth(),
            style = TextStyle(
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            ),
            onClick = { offset ->
                agreementText
                    .getStringAnnotations(
                        tag = "terms",
                        start = offset,
                        end = offset
                    )
                    .firstOrNull()
                    ?.let {
                        openWebPage(context, TERMS_URL)
                    }

                agreementText
                    .getStringAnnotations(
                        tag = "privacy",
                        start = offset,
                        end = offset
                    )
                    .firstOrNull()
                    ?.let {
                        openWebPage(context, PRIVACY_POLICY_URL)
                    }
            }
        )

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun openWebPage(
    context: Context,
    url: String
) {
    try {
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse(url)
        )

        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(
            context,
            "No browser found on this device.",
            Toast.LENGTH_SHORT
        ).show()
    }
}