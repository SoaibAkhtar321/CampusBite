package com.campusbite.app.ui.screens.auth

import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.campusbite.app.ui.theme.Orange
import com.campusbite.app.ui.theme.TextPrimary
import com.campusbite.app.ui.theme.TextSecondary
import com.campusbite.app.ui.viewmodel.AuthState
import com.campusbite.app.ui.viewmodel.AuthViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

@Composable
fun CompleteProfileScreen(
    onNavigateToStudent: () -> Unit,
    onNavigateToPending: () -> Unit,
    onNavigateToLogin: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val universities = listOf(
        "Galgotias University" to "galgotias_university",
        "Sharda University" to "sharda_university",
        "Amity University" to "amity_university",
        "Other" to "other"
    )

    val isDarkTheme = isSystemInDarkTheme()

    val screenTextColor = if (isDarkTheme) {
        Color.White
    } else {
        TextPrimary
    }

    val screenSubTextColor = if (isDarkTheme) {
        Color(0xFFBDBDBD)
    } else {
        TextSecondary
    }

    var fullName by remember {
        mutableStateOf(sanitizeNameInput(viewModel.googleName))
    }

    var nameError by remember {
        mutableStateOf<String?>(null)
    }

    var phone by remember {
        mutableStateOf("")
    }

    var phoneError by remember {
        mutableStateOf<String?>(null)
    }

    /*
     * UI label = User
     * Backend role value = "student"
     * Do not change backend role to "user" right now.
     */
    var selectedRole by remember {
        mutableStateOf("student")
    }

    var selectedUniversityName by remember {
        mutableStateOf("")
    }

    var selectedUniversityId by remember {
        mutableStateOf("")
    }

    var universityExpanded by remember {
        mutableStateOf(false)
    }

    var localError by remember {
        mutableStateOf<String?>(null)
    }

    val authState by viewModel.authState.collectAsState()

    LaunchedEffect(Unit) {
        val firebaseAuth = FirebaseAuth.getInstance()
        val currentUser = firebaseAuth.currentUser

        if (currentUser == null) {
            viewModel.resetState()
            onNavigateToLogin()
            return@LaunchedEffect
        }

        try {
            currentUser.reload().await()
        } catch (e: Exception) {
            firebaseAuth.signOut()
            viewModel.resetState()
            onNavigateToLogin()
        }
    }

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
        focusedTextColor = screenTextColor,
        unfocusedTextColor = screenTextColor,
        errorTextColor = screenTextColor,

        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        errorContainerColor = Color.Transparent,

        focusedBorderColor = Orange,
        unfocusedBorderColor = screenSubTextColor,
        errorBorderColor = MaterialTheme.colorScheme.error,

        focusedLabelColor = Orange,
        unfocusedLabelColor = screenSubTextColor,
        errorLabelColor = MaterialTheme.colorScheme.error,

        cursorColor = Orange,
        errorCursorColor = Orange
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
            fontWeight = FontWeight.Bold,
            color = screenTextColor
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Add details to continue with CampusBite",
            fontSize = 13.sp,
            color = screenSubTextColor
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = fullName,
            onValueChange = { value ->
                fullName = sanitizeNameInput(value)
                nameError = validateNameWhileTyping(fullName)
                localError = null
            },
            label = {
                Text("Full Name")
            },
            singleLine = true,
            isError = nameError != null,
            supportingText = {
                nameError?.let {
                    Text(text = it)
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                capitalization = KeyboardCapitalization.Words
            ),
            modifier = Modifier.fillMaxWidth(),
            colors = textFieldColors
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = phone,
            onValueChange = { value ->
                phone = value
                    .filter { it.isDigit() }
                    .take(10)

                phoneError = validatePhoneWhileTyping(phone)
                localError = null
            },
            label = {
                Text("Phone Number")
            },
            singleLine = true,
            isError = phoneError != null,
            supportingText = {
                phoneError?.let {
                    Text(text = it)
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number
            ),
            modifier = Modifier.fillMaxWidth(),
            colors = textFieldColors
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Use a valid phone number for order updates and refund support.",
            fontSize = 12.sp,
            color = screenSubTextColor
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Continue as",
            fontSize = 14.sp,
            color = screenSubTextColor
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FilterChip(
                selected = selectedRole == "student",
                onClick = {
                    selectedRole = "student"
                    selectedUniversityName = ""
                    selectedUniversityId = ""
                    localError = null
                },
                label = {
                    Text("User")
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
                color = screenSubTextColor
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
                        Text("Select Campus / University")
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
                val finalName = normalizeName(fullName)
                val nameValidationError = validateNameOnSubmit(finalName)
                val phoneValidationError = validatePhoneOnSubmit(phone)

                nameError = nameValidationError
                phoneError = phoneValidationError

                localError = when {
                    nameValidationError != null ->
                        nameValidationError

                    phoneValidationError != null ->
                        phoneValidationError

                    selectedRole == "shopkeeper" && selectedUniversityName.isBlank() ->
                        "Please select your campus/university"

                    else -> null
                }

                if (localError == null) {
                    viewModel.completeGoogleProfile(
                        name = finalName,
                        phone = phone,
                        role = selectedRole,
                        university = if (selectedRole == "shopkeeper") {
                            selectedUniversityName
                        } else {
                            ""
                        },
                        universityId = if (selectedRole == "shopkeeper") {
                            selectedUniversityId
                        } else {
                            ""
                        }
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

private fun sanitizeNameInput(
    value: String
): String {
    return value
        .filter { char ->
            char.isLetter() ||
                    char == ' ' ||
                    char == '.' ||
                    char == '\'' ||
                    char == '-'
        }
        .replace(Regex("\\s+"), " ")
        .take(50)
}

private fun normalizeName(
    name: String
): String {
    return name
        .trim()
        .replace(Regex("\\s+"), " ")
}

private fun validateNameWhileTyping(
    name: String
): String? {
    val cleanName = normalizeName(name)

    if (cleanName.isBlank()) {
        return null
    }

    return validateNameCommon(
        name = cleanName,
        allowBlank = true
    )
}

private fun validateNameOnSubmit(
    name: String
): String? {
    val cleanName = normalizeName(name)

    return validateNameCommon(
        name = cleanName,
        allowBlank = false
    )
}

private fun validateNameCommon(
    name: String,
    allowBlank: Boolean
): String? {
    val cleanName = normalizeName(name)

    if (cleanName.isBlank()) {
        return if (allowBlank) {
            null
        } else {
            "Name is required"
        }
    }

    val letterCount = cleanName.count { it.isLetter() }

    return when {
        cleanName.length < 2 ->
            "Name must be at least 2 characters"

        cleanName.length > 50 ->
            "Name must be less than 50 characters"

        letterCount < 2 ->
            "Name must contain at least 2 letters"

        startsOrEndsWithNameSeparator(cleanName) ->
            "Name cannot start or end with special characters"

        hasConsecutiveNameSeparators(cleanName) ->
            "Name contains invalid repeated special characters"

        hasTooManyRepeatedNameCharacters(cleanName) ->
            "Enter a valid name"

        isPlaceholderName(cleanName) ->
            "Please enter your real name"

        else -> null
    }
}

private fun startsOrEndsWithNameSeparator(
    name: String
): Boolean {
    val separators = setOf('.', '\'', '-')

    return name.firstOrNull() in separators ||
            name.lastOrNull() in separators
}

private fun hasConsecutiveNameSeparators(
    name: String
): Boolean {
    val invalidPatterns = listOf(
        "..",
        "--",
        "''",
        ".-",
        "-.",
        ".'",
        "'.",
        "-'",
        "'-"
    )

    return invalidPatterns.any { pattern ->
        name.contains(pattern)
    }
}

private fun hasTooManyRepeatedNameCharacters(
    name: String
): Boolean {
    val lettersOnly = name
        .filter { it.isLetter() }
        .lowercase()

    if (lettersOnly.length < 4) {
        return false
    }

    val hasSingleCharacterTooManyTimes = lettersOnly
        .groupingBy { it }
        .eachCount()
        .any { it.value >= 6 }

    if (hasSingleCharacterTooManyTimes) {
        return true
    }

    var repeatCount = 1

    for (i in 1 until lettersOnly.length) {
        if (lettersOnly[i] == lettersOnly[i - 1]) {
            repeatCount++

            if (repeatCount >= 4) {
                return true
            }
        } else {
            repeatCount = 1
        }
    }

    return false
}

private fun isPlaceholderName(
    name: String
): Boolean {
    val compactName = name
        .lowercase()
        .replace(" ", "")
        .replace(".", "")
        .replace("-", "")
        .replace("'", "")

    val blockedNames = setOf(
        "test",
        "testing",
        "user",
        "username",
        "admin",
        "administrator",
        "unknown",
        "noname",
        "name",
        "student",
        "shopkeeper",
        "vendor",
        "customer",
        "campusbite",
        "asdf",
        "qwerty",
        "abcd",
        "abcde",
        "xyz"
    )

    return compactName in blockedNames
}

private fun validatePhoneWhileTyping(
    phone: String
): String? {
    val cleanPhone = phone.trim()

    if (cleanPhone.isBlank()) {
        return null
    }

    return when {
        cleanPhone.first() !in listOf('6', '7', '8', '9') ->
            "Phone number must start with 6, 7, 8, or 9"

        cleanPhone.length < 10 ->
            "Phone number must be 10 digits"

        isFakePhoneNumber(cleanPhone) ->
            "Enter a valid phone number"

        else -> null
    }
}

private fun validatePhoneOnSubmit(
    phone: String
): String? {
    val cleanPhone = phone.trim()

    return when {
        cleanPhone.isBlank() ->
            "Phone number is required"

        cleanPhone.length != 10 ->
            "Phone number must be 10 digits"

        cleanPhone.first() !in listOf('6', '7', '8', '9') ->
            "Phone number must start with 6, 7, 8, or 9"

        isFakePhoneNumber(cleanPhone) ->
            "Enter a valid phone number"

        else -> null
    }
}

private fun isFakePhoneNumber(
    phone: String
): Boolean {
    val fakeNumbers = setOf(
        "1234567890",
        "9876543210",
        "0123456789",
        "0000000000",
        "1111111111",
        "2222222222",
        "3333333333",
        "4444444444",
        "5555555555",
        "6666666666",
        "7777777777",
        "8888888888",
        "9999999999"
    )

    return phone in fakeNumbers ||
            hasTooManySameDigits(phone) ||
            hasLongRepeatedSequence(phone)
}

private fun hasTooManySameDigits(
    phone: String
): Boolean {
    return phone
        .groupingBy { it }
        .eachCount()
        .any { it.value >= 8 }
}

private fun hasLongRepeatedSequence(
    phone: String
): Boolean {
    var repeatCount = 1

    for (i in 1 until phone.length) {
        if (phone[i] == phone[i - 1]) {
            repeatCount++

            if (repeatCount >= 6) {
                return true
            }
        } else {
            repeatCount = 1
        }
    }

    return false
}