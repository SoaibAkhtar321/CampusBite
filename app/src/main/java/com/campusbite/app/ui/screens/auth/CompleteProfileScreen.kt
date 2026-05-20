package com.campusbite.app.ui.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.campusbite.app.ui.theme.Orange
import com.campusbite.app.ui.theme.TextPrimary
import com.campusbite.app.ui.theme.TextSecondary
import com.campusbite.app.ui.viewmodel.AuthState
import com.campusbite.app.ui.viewmodel.AuthViewModel

@Composable
fun CompleteProfileScreen(
    onNavigateToStudent: () -> Unit,
    onNavigateToPending: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val universities = listOf(
        "Galgotias University" to "galgotias_university",
        "Sharda University" to "sharda_university",
        "Amity University" to "amity_university",
        "Other" to "other"
    )

    var phone by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf("student") }
    var selectedUniversityName by remember { mutableStateOf("") }
    var selectedUniversityId by remember { mutableStateOf("") }
    var universityExpanded by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    val authState by viewModel.authState.collectAsState()

    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.StudentSuccess -> {
                viewModel.resetState()
                onNavigateToStudent()
            }

            is AuthState.ShopkeeperPending -> {
                viewModel.resetState()
                onNavigateToPending()
            }

            else -> Unit
        }
    }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary,
        focusedBorderColor = Orange,
        unfocusedBorderColor = TextSecondary,
        focusedLabelColor = Orange,
        unfocusedLabelColor = TextSecondary,
        cursorColor = Orange
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Complete Profile",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Add details to continue with CampusBite",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FilterChip(
                selected = selectedRole == "student",
                onClick = {
                    selectedRole = "student"
                    localError = null
                },
                label = {
                    Text("Student")
                }
            )

            FilterChip(
                selected = selectedRole == "shopkeeper",
                onClick = {
                    selectedRole = "shopkeeper"
                    localError = null
                },
                label = {
                    Text("Shopkeeper")
                }
            )
        }

        if (selectedRole == "shopkeeper") {
            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Shopkeeper accounts require admin approval.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = phone,
            onValueChange = { value ->
                phone = value.filter { it.isDigit() }.take(10)
                localError = null
            },
            label = {
                Text("Phone Number")
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Phone
            ),
            modifier = Modifier.fillMaxWidth(),
            colors = textFieldColors
        )

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = selectedUniversityName,
                onValueChange = {},
                readOnly = true,
                label = {
                    Text("Select University")
                },
                modifier = Modifier.fillMaxWidth(),
                colors = textFieldColors,
                trailingIcon = {
                    TextButton(
                        onClick = {
                            universityExpanded = true
                        }
                    ) {
                        Text(
                            text = "Choose",
                            color = Orange
                        )
                    }
                }
            )

            DropdownMenu(
                expanded = universityExpanded,
                onDismissRequest = {
                    universityExpanded = false
                }
            ) {
                universities.forEach { (name, id) ->
                    DropdownMenuItem(
                        text = {
                            Text(name)
                        },
                        onClick = {
                            selectedUniversityName = name
                            selectedUniversityId = id
                            universityExpanded = false
                            localError = null
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val errorMessage = when {
            localError != null -> localError
            authState is AuthState.Error -> (authState as AuthState.Error).message
            else -> null
        }

        if (errorMessage != null) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(8.dp))
        }

        Button(
            onClick = {
                localError = when {
                    phone.length != 10 -> "Enter a valid 10 digit phone number"
                    selectedUniversityName.isBlank() -> "Please select your university"
                    else -> null
                }

                if (localError == null) {
                    viewModel.completeGoogleProfile(
                        phone = phone,
                        role = selectedRole,
                        university = selectedUniversityName,
                        universityId = selectedUniversityId
                    )
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
                Text("Continue")
            }
        }
    }
}