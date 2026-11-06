package mihon.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.neon.NeonChip
import eu.kanade.presentation.more.settings.widget.SettingsCardGroup
import eu.kanade.presentation.more.settings.widget.SwitchPreferenceWidget
import eu.kanade.presentation.theme.NeonSpacing
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.extension.ExtensionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import mihon.domain.extension.model.ContentWarning
import tachiyomi.core.common.preference.toggle
import tachiyomi.domain.source.model.StubSource
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import tachiyomi.source.local.isLocal

/**
 * What recommendations leave out: titles for adults, sources, and genres.
 */
class RecommendationSettingsScreen : Screen() {

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = metroViewModel<RecommendationSettingsViewModel>()
        val state by viewModel.state.collectAsStateWithLifecycle()
        val preferences = viewModel.preferences
        val hideAdult by preferences.hideAdultContent.collectAsState()
        val excludedSources by preferences.excludedSources.collectAsState()
        val excludedGenres by preferences.excludedGenres.collectAsState()

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(MR.strings.recommendation_settings),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            LazyColumn(contentPadding = contentPadding) {
                item(key = "content") {
                    SettingsCardGroup(title = stringResource(MR.strings.recommendation_settings_content)) {
                        SwitchPreferenceWidget(
                            title = stringResource(MR.strings.recommendation_hide_adult),
                            subtitle = stringResource(MR.strings.recommendation_hide_adult_summary),
                            checked = hideAdult,
                            onCheckedChanged = { preferences.hideAdultContent.toggle() },
                        )
                    }
                }
                item(key = "genres") {
                    SettingsCardGroup(title = stringResource(MR.strings.recommendation_settings_genres)) {
                        Text(
                            text = stringResource(MR.strings.recommendation_genres_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(NeonSpacing.md),
                        )
                        FlowRow(
                            modifier = Modifier.padding(
                                start = NeonSpacing.md,
                                end = NeonSpacing.md,
                                bottom = NeonSpacing.md,
                            ),
                            horizontalArrangement = Arrangement.spacedBy(NeonSpacing.xs),
                            verticalArrangement = Arrangement.spacedBy(NeonSpacing.xs),
                        ) {
                            (state.genres + excludedGenres).distinct().forEach { key ->
                                val excluded = key in excludedGenres
                                NeonChip(
                                    text = Genres.label(key),
                                    selected = excluded,
                                    accent = if (excluded) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    onClick = {
                                        preferences.excludedGenres.set(
                                            if (excluded) excludedGenres - key else excludedGenres + key,
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
                item(key = "sources") {
                    SettingsCardGroup(title = stringResource(MR.strings.recommendation_settings_sources)) {
                        if (state.sources.isEmpty()) {
                            Text(
                                text = stringResource(MR.strings.home_notice_no_sources),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(NeonSpacing.md),
                            )
                        }
                        state.sources.forEach { source ->
                            val hiddenAsAdult = hideAdult && source.isAdult
                            SwitchPreferenceWidget(
                                title = source.name,
                                subtitle = if (hiddenAsAdult) {
                                    stringResource(MR.strings.recommendation_source_hidden_adult)
                                } else {
                                    source.lang.uppercase()
                                },
                                checked = !hiddenAsAdult && "${source.id}" !in excludedSources,
                                onCheckedChanged = { use ->
                                    val id = "${source.id}"
                                    preferences.excludedSources.set(
                                        if (use) excludedSources - id else excludedSources + id,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class RecommendationSettingsViewModel(
    val preferences: HomePreferences,
    private val repository: HomeFeedRepository,
    private val sourceManager: SourceManager,
    private val sourcePreferences: SourcePreferences,
    private val extensionManager: ExtensionManager,
) : ViewModel() {

    data class SourceItem(val id: Long, val name: String, val lang: String, val isAdult: Boolean)

    data class State(val sources: List<SourceItem> = emptyList(), val genres: List<String> = Genres.common)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val adult = extensionManager.getLoadedExtensions()
                .filter { it.contentWarning == ContentWarning.NSFW }
                .flatMapTo(HashSet()) { extension -> extension.sources.map { it.id } }
            val languages = sourcePreferences.enabledLanguages.get()
            val sources = sourceManager.getAll()
                .filter { !it.isLocal() && it !is StubSource && it.lang in languages }
                .sortedBy { it.name.lowercase() }
                .map { SourceItem(it.id, it.name, it.lang, it.id in adult) }
            val favourites = repository.profile().topGenres(GENRES_FROM_PROFILE).map { it.key }
            _state.value = State(sources = sources, genres = (favourites + Genres.common).distinct())
        }
    }

    private companion object {
        const val GENRES_FROM_PROFILE = 12
    }
}
