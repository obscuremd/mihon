package eu.kanade.presentation.more.settings.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.domain.manga.interactor.SetMangaViewerFlags
import eu.kanade.domain.manga.model.readingMode
import eu.kanade.presentation.category.visualName
import eu.kanade.presentation.components.NeonTabRow
import eu.kanade.presentation.components.neon.GhostButton
import eu.kanade.presentation.components.neon.NeonButton
import eu.kanade.presentation.components.neon.NeonChip
import eu.kanade.presentation.components.neon.NeonIconButton
import eu.kanade.presentation.theme.NeonDefaults
import eu.kanade.presentation.theme.NeonSpacing
import eu.kanade.presentation.theme.cardSurface
import eu.kanade.presentation.theme.neonSurface
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import eu.kanade.tachiyomi.ui.reader.setting.ReadingModeRule
import eu.kanade.tachiyomi.ui.reader.setting.ReadingModeRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Add
import mihon.icons.materialsymbols.rounded.ArrowUpward
import mihon.icons.materialsymbols.rounded.Delete
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

/**
 * Edits the rules that pick a series' reading mode from its tags or library categories: add, change, reorder and
 * delete them, or start from the suggested ones. Series that have a reading mode of their own aren't ruled, so it
 * says how many a rule would apply to and lets their own modes go.
 */
@Composable
fun ReadingModeRulesEditor() {
    val viewModel = metroViewModel<ReadingModeRulesViewModel>()
    val preference = viewModel.preferences.readingModeRules
    val raw by preference.collectAsState()
    val rules = remember(raw) { ReadingModeRules.parse(raw) }
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val overridden by viewModel.overridden.collectAsStateWithLifecycle()
    // The rule being edited: its index, -1 for a new one, null for none
    var editing by remember { mutableStateOf<Int?>(null) }
    val save = { updated: List<ReadingModeRule> -> preference.set(ReadingModeRules.serialize(updated)) }

    Column(
        modifier = Modifier.padding(horizontal = NeonSpacing.gutter, vertical = NeonSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(NeonSpacing.xs),
    ) {
        Text(
            text = stringResource(MR.strings.pref_reading_mode_rules_summary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (overridden > 0) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .neonSurface(NeonDefaults.CardShape, MaterialTheme.colorScheme.tertiary)
                    .padding(NeonSpacing.md),
                verticalArrangement = Arrangement.spacedBy(NeonSpacing.xs),
            ) {
                Text(
                    text = pluralStringResource(MR.plurals.reading_mode_rule_overridden, overridden, overridden),
                    style = MaterialTheme.typography.bodyMedium,
                )
                NeonButton(
                    text = stringResource(MR.strings.reading_mode_rule_use_rules),
                    onClick = viewModel::letRulesDecide,
                    accent = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
        rules.forEachIndexed { index, rule ->
            RuleRow(
                rule = rule,
                categories = categories,
                onClick = { editing = index },
                onMoveUp = if (index > 0) {
                    { save(rules.toMutableList().apply { add(index - 1, removeAt(index)) }) }
                } else {
                    null
                },
                onDelete = { save(rules.filterIndexed { i, _ -> i != index }) },
            )
        }
        Row(
            modifier = Modifier.padding(top = NeonSpacing.xxs),
            horizontalArrangement = Arrangement.spacedBy(NeonSpacing.xs),
        ) {
            NeonButton(
                text = stringResource(MR.strings.reading_mode_rule_add),
                icon = MaterialSymbols.Rounded.Add,
                onClick = { editing = -1 },
            )
            if (rules.isEmpty()) {
                GhostButton(
                    text = stringResource(MR.strings.reading_mode_rule_suggested),
                    onClick = { save(ReadingModeRules.suggested) },
                )
            }
        }
    }

    editing?.let { index ->
        RuleDialog(
            initial = rules.getOrNull(index),
            categories = categories,
            onDismiss = { editing = null },
            onConfirm = { rule ->
                save(if (index < 0) rules + rule else rules.mapIndexed { i, old -> if (i == index) rule else old })
                editing = null
            },
        )
    }
}

@Composable
private fun RuleRow(
    rule: ReadingModeRule,
    categories: List<Category>,
    onClick: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .cardSurface()
            .padding(start = NeonSpacing.md, end = NeonSpacing.xs, top = NeonSpacing.xs, bottom = NeonSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NeonSpacing.xs),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(vertical = NeonSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NeonSpacing.xs),
            ) {
                NeonChip(
                    text = stringResource(
                        when (rule.kind) {
                            ReadingModeRule.Kind.Tag -> MR.strings.reading_mode_rule_kind_tag
                            ReadingModeRule.Kind.Category -> MR.strings.reading_mode_rule_kind_category
                        },
                    ),
                    accent = if (rule.kind == ReadingModeRule.Kind.Category) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.secondary
                    },
                )
                Text(
                    text = rule.label(categories),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = stringResource(rule.mode.stringRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (onMoveUp != null) {
            NeonIconButton(
                icon = MaterialSymbols.Rounded.ArrowUpward,
                contentDescription = stringResource(MR.strings.reading_mode_rule_move_up),
                onClick = onMoveUp,
            )
        }
        NeonIconButton(
            icon = MaterialSymbols.Rounded.Delete,
            contentDescription = stringResource(MR.strings.action_delete),
            onClick = onDelete,
        )
    }
}

/** What [this] rule is for, as shown: its tag, or its category's name. */
@Composable
private fun ReadingModeRule.label(categories: List<Category>): String = when (kind) {
    ReadingModeRule.Kind.Tag -> value
    ReadingModeRule.Kind.Category -> categories.find { it.id == categoryId }?.visualName
        ?: stringResource(MR.strings.reading_mode_rule_missing_category)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RuleDialog(
    initial: ReadingModeRule?,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onConfirm: (ReadingModeRule) -> Unit,
) {
    var kind by rememberSaveable { mutableStateOf(initial?.kind ?: ReadingModeRule.Kind.Category) }
    var tag by rememberSaveable {
        mutableStateOf(initial?.takeIf { it.kind == ReadingModeRule.Kind.Tag }?.value.orEmpty())
    }
    var categoryId by rememberSaveable { mutableStateOf(initial?.categoryId) }
    var mode by rememberSaveable { mutableStateOf(initial?.mode ?: ReadingMode.BOOK_RIGHT_TO_LEFT) }
    val rule = when (kind) {
        ReadingModeRule.Kind.Tag -> tag.takeIf { it.isNotBlank() }?.let { ReadingModeRule.tag(it, mode) }
        ReadingModeRule.Kind.Category -> categoryId?.let { ReadingModeRule.category(it, mode) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(MR.strings.reading_mode_rule)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(NeonSpacing.sm)) {
                NeonTabRow(
                    selectedTabIndex = kind.ordinal,
                    tabCount = ReadingModeRule.Kind.entries.size,
                    onTabClick = { kind = ReadingModeRule.Kind.entries[it] },
                    modifier = Modifier.padding(horizontal = 0.dp),
                ) { index, _ ->
                    Text(
                        stringResource(
                            when (ReadingModeRule.Kind.entries[index]) {
                                ReadingModeRule.Kind.Tag -> MR.strings.reading_mode_rule_kind_tag
                                ReadingModeRule.Kind.Category -> MR.strings.reading_mode_rule_kind_category
                            },
                        ),
                    )
                }
                when (kind) {
                    ReadingModeRule.Kind.Tag -> OutlinedTextField(
                        value = tag,
                        onValueChange = { tag = it },
                        label = { Text(stringResource(MR.strings.reading_mode_rule_tag)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ReadingModeRule.Kind.Category -> if (categories.isEmpty()) {
                        Text(
                            text = stringResource(MR.strings.reading_mode_rule_no_categories),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(NeonSpacing.xs)) {
                            categories.forEach {
                                FilterChip(
                                    selected = it.id == categoryId,
                                    onClick = { categoryId = it.id },
                                    label = { Text(it.visualName) },
                                )
                            }
                        }
                    }
                }
                Text(
                    text = stringResource(MR.strings.reading_mode_rule_reads_as),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(NeonSpacing.xs)) {
                    ReadingMode.entries.filter { it != ReadingMode.DEFAULT }.forEach {
                        FilterChip(
                            selected = it == mode,
                            onClick = { mode = it },
                            label = { Text(stringResource(it.stringRes)) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { rule?.let(onConfirm) }, enabled = rule != null) {
                Text(stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
    )
}

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class ReadingModeRulesViewModel(
    getCategories: GetCategories,
    getLibraryManga: GetLibraryManga,
    private val setMangaViewerFlags: SetMangaViewerFlags,
    val preferences: ReaderPreferences,
) : ViewModel() {

    /** The reader's own categories, which rules can be for. */
    val categories: StateFlow<List<Category>> = getCategories.subscribe()
        .map { categories -> categories.filterNot { it.isSystemCategory } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Library series a rule is for that have a reading mode of their own, which the rule then doesn't change. */
    private val overriddenSeries: StateFlow<List<LibraryManga>> = combine(
        getLibraryManga.subscribe(),
        preferences.readingModeRules.changes(),
    ) { library, raw ->
        val rules = ReadingModeRules.parse(raw)
        if (rules.isEmpty()) return@combine emptyList()
        library.filter {
            it.manga.readingMode.toInt() != ReadingMode.DEFAULT.flagValue &&
                ReadingModeRules.match(rules, it.manga.genre, it.categories) != null
        }
    }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val overridden: StateFlow<Int> = overriddenSeries
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** Lets go of the own reading modes of the series rules are for, so the rules pick theirs. */
    fun letRulesDecide() {
        val series = overriddenSeries.value
        viewModelScope.launch(Dispatchers.IO) {
            series.forEach { setMangaViewerFlags.awaitSetReadingMode(it.id, ReadingMode.DEFAULT.flagValue.toLong()) }
        }
    }
}
