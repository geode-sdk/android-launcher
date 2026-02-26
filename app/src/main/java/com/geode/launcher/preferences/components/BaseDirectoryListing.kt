package com.geode.launcher.preferences.components

import androidx.activity.OnBackPressedDispatcher
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.geode.launcher.R
import com.geode.launcher.log.BaseDirectoryViewModel
import com.geode.launcher.ui.theme.Typography

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrashLogsTopBar(
    onBackPressedDispatcher: OnBackPressedDispatcher?,
    directoryViewModel: BaseDirectoryViewModel<*>,
    scrollBehavior: TopAppBarScrollBehavior,
    @StringRes titleId: Int,
    additionalOptions: (@Composable (() -> Unit) -> Unit)? = null,
) {
    val isLoading by directoryViewModel.isLoading.collectAsState()
    var showMoreOptions by remember { mutableStateOf(false) }

    Column {
        TopAppBar(
            navigationIcon = {
                IconButton(onClick = { onBackPressedDispatcher?.onBackPressed() }) {
                    Icon(
                        painterResource(R.drawable.icon_arrow_back),
                        contentDescription = stringResource(R.string.back_icon_alt)
                    )
                }
            },
            actions = {
                IconButton(onClick = { showMoreOptions = !showMoreOptions }) {
                    Icon(
                        painterResource(R.drawable.icon_more_vert),
                        contentDescription = stringResource(R.string.application_logs_more)
                    )
                }

                DropdownMenu(
                    expanded = showMoreOptions,
                    onDismissRequest = { showMoreOptions = false }
                ) {
                    DropdownMenuItem(
                        leadingIcon = {
                            Icon(
                                painterResource(R.drawable.icon_delete),
                                contentDescription = null
                            )
                        },
                        text = {
                            Text(stringResource(R.string.application_logs_delete))
                        }, onClick = {
                            directoryViewModel.clearAllFiles()
                            showMoreOptions = false
                        }
                    )

                    additionalOptions?.invoke {
                        showMoreOptions = false
                    }
                }
            },
            title = {
                Text(
                    stringResource(titleId),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            scrollBehavior = scrollBehavior,
        )

        if (isLoading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun <T> CrashLogsListing(
    modifier: Modifier = Modifier,
    directoryViewModel: BaseDirectoryViewModel<T>,
    @StringRes noneAvailableId: Int,
    logCard: @Composable (T, Shape) -> Unit,
) {
    val logLines = directoryViewModel.lineState
    val isLoading by directoryViewModel.isLoading.collectAsState()

    LazyColumn(
        modifier,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        if (!isLoading && logLines.isEmpty()) {
            item {
                Text(
                    stringResource(noneAvailableId),
                    textAlign = TextAlign.Center,
                    style = Typography.bodyLarge,
                    modifier = Modifier.fillMaxSize().padding(8.dp)
                )
            }
        }

        val itemsSize = logLines.size

        itemsIndexed(
            logLines,
            key = { index, line -> directoryViewModel.getItemKey(line) }
        ) { index, line ->
            val shape = RoundedCornerShape(
                topStart = if (index == 0) 16.dp else 4.dp,
                topEnd = if (index == 0) 16.dp else 4.dp,
                bottomStart = if (index + 1 == itemsSize) 16.dp else 4.dp,
                bottomEnd = if (index + 1 == itemsSize) 16.dp else 4.dp
            )

            logCard(line, shape)
        }

        item {
            Spacer(Modifier.height(4.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> DirectoryListingScreen(
    onBackPressedDispatcher: OnBackPressedDispatcher?,
    directoryViewModel: BaseDirectoryViewModel<T>,
    @StringRes titleId: Int,
    @StringRes noneAvailableId: Int,
    additionalOptions: (@Composable (() -> Unit) -> Unit)? = null,
    logCard: @Composable (T, Shape) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CrashLogsTopBar(
                onBackPressedDispatcher,
                directoryViewModel,
                scrollBehavior,
                titleId,
                additionalOptions = additionalOptions
            )
        }
    ) { innerPadding ->
        CrashLogsListing(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxWidth(),
            directoryViewModel,
            noneAvailableId = noneAvailableId,
            logCard = logCard
        )
    }
}
