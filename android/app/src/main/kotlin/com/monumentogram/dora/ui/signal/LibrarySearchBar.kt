package com.monumentogram.dora.ui.signal

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import com.monumentogram.dora.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibrarySearchBar(library: RecordingLibrary) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }
    TopAppBar(
        title = {
            TextField(
                value = library.query,
                onValueChange = library::search,
                modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("library-search"),
                placeholder = { Text(stringResource(R.string.signal_search_hint)) },
                singleLine = true,
                keyboardOptions =
                    KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                trailingIcon = {
                    if (library.query.isNotEmpty())
                        IconButton(onClick = { library.search("") }) {
                            Icon(
                                painterResource(R.drawable.ic_signal_close),
                                stringResource(R.string.signal_search_clear),
                            )
                        }
                },
            )
        },
        navigationIcon = {
            IconButton(
                onClick = {
                    keyboard?.hide()
                    library.closeSearch()
                }
            ) {
                Icon(
                    painterResource(R.drawable.ic_signal_back),
                    stringResource(R.string.signal_search_close),
                )
            }
        },
    )
}

@Composable
internal fun highlighted(text: String, query: String) = buildAnnotatedString {
    append(text)
    if (query.isNotEmpty()) {
        var start = text.indexOf(query, ignoreCase = true)
        while (start >= 0) {
            addStyle(
                SpanStyle(
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                ),
                start,
                start + query.length,
            )
            start = text.indexOf(query, start + query.length, ignoreCase = true)
        }
    }
}
