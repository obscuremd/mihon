# Neon — Mihon's design language

A minimal, dark-first interface where the art is the color and the chrome stays quiet. Accents are thin
neon lines and soft tints, never solid blocks. Everything is spaced to breathe, and the whole interface
scales to the reader's preferred size.

This document is the source of truth for every screen. The tokens and components named here live in
`app/src/main/java/eu/kanade/presentation/theme/Neon.kt` and `.../presentation/components/neon/`.

---

## 1. Principles

1. **Content first.** Covers carry the color. Surfaces, bars and controls recede so the art stands out.
2. **Neon accents, not neon fills.** The accent shows up as a gradient *border* on a *transparent tint* of
   itself. Nothing in the app is filled with a solid gradient.
3. **Air over lines.** Space separates things; dividers are a last resort. One idea per surface.
4. **Theme-driven.** Every color comes from the active theme's color scheme, so Neon, Strawberry, Dynamic and
   every other theme get the same language in their own colors.
5. **Sized for the reader.** Display size (XS–XL) scales the whole interface: type, icons, spacing and touch
   targets together.

## 2. Color

All roles map to the Material color scheme of the selected theme.

| Role | Token | Used for |
| --- | --- | --- |
| Canvas | `background` | Screen backgrounds |
| Surface | `surfaceContainerLow` | Cards, rows, fields |
| Raised surface | `surfaceContainer` | Settings groups, dialogs, sheets |
| Hairline | `outlineVariant` | Quiet borders on cards and rows |
| Accent | `primary` | Neon strokes, tints, active states, accent text |
| Accent text | `primary` | Text and icons inside accent controls |
| Secondary text | `onSurfaceVariant` | Metadata, subtitles, helper text |

### The neon accent

- **Tint fill**: the accent at **12%** alpha at rest, **20%** when selected or pressed.
- **Neon stroke**: a linear gradient from a light end to the accent. In dark themes the light end is the
  accent mixed 55% toward white; in light themes it's the accent mixed 30% toward the background, so the
  stroke stays visible on light canvases. Width **1dp** at rest, **1.5dp** selected or focused.
- **Glow**: an accent-colored shadow, at most **10dp**, only on featured covers and the one primary action of
  a screen. Never on lists.
- Accent text sits on the tint fill, so it always meets contrast. Never put `onPrimary` text on a tint.

### Genre accents

Genres keep a fixed hue each (Action red, Romance pink, Fantasy blue, Horror brown, Comedy yellow, Drama
purple, Sci-Fi cyan, Adventure orange, Slice of Life green, Mystery grey…). In light themes the hue is darkened
30% so it reads on light surfaces. They follow the same tint-plus-stroke pattern.

## 3. Spacing

A 4dp base with a deliberate, roomy scale.

| Token | Value | Typical use |
| --- | --- | --- |
| `xxs` | 4dp | Icon to label |
| `xs` | 8dp | Inside chips, between stacked metadata lines |
| `sm` | 12dp | Between chips, card content gaps |
| `md` | 16dp | Card padding, row padding |
| `lg` | 20dp | **Screen gutter** (left and right edges) |
| `xl` | 24dp | Between groups inside a section |
| `xxl` | 32dp | **Between sections** |
| `xxxl` | 48dp | Top of empty states, around hero content |

Layout rules:

- Screen gutter is **20dp** everywhere.
- Sections are **32dp** apart; a section header sits **12dp** above its content.
- Grids: **14dp** between columns, **22dp** between rows.
- Lists: rows are cards with **8dp** between them, not dividers. Row padding **16dp**, minimum height **64dp**.
- Touch targets are at least **44dp** (before display-size scaling).

The shared `MaterialTheme.padding` scale follows this: `extraSmall` 6, `small` 12, `medium` 20, `large` 28,
`extraLarge` 40.

## 4. Shape

| Token | Radius | Used for |
| --- | --- | --- |
| `extraSmall` | 8dp | Badges |
| `small` | 12dp | Covers, icon tiles, icon buttons |
| `medium` | 16dp | Cards, rows |
| `large` | 24dp | Featured cards, sheets |
| `extraLarge` | 32dp | Dialogs |
| `pill` | 50% | Buttons, chips, tabs, search fields |

## 5. Typography

The Material type scale with heavier titles.

| Style | Weight | Used for |
| --- | --- | --- |
| `headlineSmall` | Bold | Featured titles (hero, details) |
| `titleLarge` | Bold | Screen and section titles |
| `titleMedium` | SemiBold | Card titles in lists |
| `titleSmall` | SemiBold | Cover titles, row titles |
| `bodyMedium` | Regular | Descriptions |
| `bodySmall` | Regular | Metadata, subtitles |
| `labelLarge` | Medium | Buttons, tabs, chips |
| Group label | `labelLarge` Bold, caps, 1.5sp tracking, accent | Settings group headers |

Cover titles take two lines; metadata takes one, joined with ` · `.

## 6. Display size

A setting in **Appearance** with five steps. It multiplies density and font scale for the whole app,
reader menus included.

| Step | Scale |
| --- | --- |
| XS | 0.80 |
| S | 0.90 |
| M (default) | 1.00 |
| L | 1.10 |
| XL | 1.20 |

## 7. Motion

- Standard transitions 200–300ms, fast-out-slow-in.
- Selection indicators move with a soft spring (damping 0.8).
- Featured carousels advance every 6s and stop while touched.
- No motion conveys meaning on its own.

## 8. Components

### Buttons

| Kind | Fill | Border | Content | Use |
| --- | --- | --- | --- | --- |
| **Neon** (primary) | Accent tint 12% | Neon stroke 1.5dp | Accent | The main action of a surface: Read now, Resume, Start reading |
| **Ghost** (secondary) | Surface | Hairline | `onSurface` | Other actions |
| **Icon** | Surface at 85% | Hairline, accent stroke when active | `onSurface` / accent | Toolbar and inline actions, 44dp, radius 12 |

The primary neon button may glow (10dp); others never do. Pills are 44dp tall with 18dp side padding.

### Chips

Pill, tint fill 10–12%, 1dp stroke of the accent (or genre hue) at 50%, `labelLarge`. Selected chips use the
20% tint and the neon stroke.

### Segmented tabs

A pill container with a hairline border. The selected tab slides as a pill with the accent tint and neon
stroke; its label turns accent and bold. Unselected labels are `onSurfaceVariant`.

### Cards

`surfaceContainerLow`, radius 16, hairline border, 16dp padding. Featured cards (hero, continue reading) use
the neon stroke and may glow.

### Cover card

Cover radius 12 with a 1dp accent stroke at 25%, then the title (two lines, `titleSmall` bold) and one line of
genres. A small accent dot marks entries in the library.

### Section header

A 3dp × 22dp accent bar, the title (`titleLarge` bold), an optional subtitle (`bodySmall`), and a ghost
"See all" pill with a chevron.

### List row

The shape every list screen uses: a card row with a leading cover (48×72) or icon tile (44dp), title and up to
two metadata lines, and trailing icon buttons. Rows are 8dp apart.

### Icon tile

40–44dp, radius 12, the icon's color at 15% behind it. Settings rotate through `primary`, `tertiary` and
`secondary` so neighbors differ.

### Settings group

A caps group label in the accent, then a raised-surface card with the rows of that group, no dividers.

### Search field

Pill, `surfaceContainerLow`, hairline accent border at 45%; the neon stroke when focused. Leading search icon in
the accent.

### Notice card

A card with an icon tile, a title, one or two lines of explanation, and at most one neon action.

### Empty state

Centered, 48dp from the top of the content, a short sentence and at most one action.

## 9. Screens

- **Home** — featured carousel full-bleed under a fading top bar; notice card when the feed isn't
  personalized; top-genre chips; sections of cover rows. Gutter 20dp, sections 32dp apart.
- **Discover** — segmented tabs (Discover, Sources, Extensions, Migrate); search field; recent and trending
  chips; genre tiles two to a row.
- **Library** — segmented category tabs; cover grid with titles and genres.
- **Details** — blurred cover backdrop fading into the canvas; cover with glow; title, author, status and
  source chips; neon "Start reading" / "Resume"; ghost actions (library, tracking, web view); description
  card with "Read more"; genre chips; chapters as list rows.
- **History, Updates, Download queue** — date headers as quiet caps labels; list rows; neon icon buttons for
  resume and download.
- **Extensions, Sources** — list rows with icon tiles; install/update as neon pills, others ghost.
- **Settings, More** — settings groups with icon tiles.

## 10. Accessibility

- Text meets 4.5:1 against what it sits on; accent text only on tints or the canvas.
- State is never shown by color alone: selected tabs are bold, library entries get a dot and a label.
- Touch targets stay at least 44dp at every display size from S up; XS is for readers who choose density.

## 11. Do and don't

- **Do** use the tint-plus-stroke pattern for anything accented.
- **Do** let space separate sections and rows.
- **Do** take colors from the theme.
- **Don't** fill anything with a gradient.
- **Don't** add glows to lists or more than one action per screen.
- **Don't** hard-code colors outside genre accents.
- **Don't** stack dividers; use spacing or a card.
