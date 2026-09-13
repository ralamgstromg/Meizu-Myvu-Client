---
name: Cupertino Pro Native
colors:
  surface: '#fcf8fb'
  surface-dim: '#dcd9dc'
  surface-bright: '#fcf8fb'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f6f3f5'
  surface-container: '#f0edef'
  surface-container-high: '#eae7ea'
  surface-container-highest: '#e4e2e4'
  on-surface: '#1b1b1d'
  on-surface-variant: '#464554'
  inverse-surface: '#303032'
  inverse-on-surface: '#f3f0f2'
  outline: '#777585'
  outline-variant: '#c7c4d6'
  surface-tint: '#4f4ccd'
  primary: '#3f3bbd'
  on-primary: '#ffffff'
  primary-container: '#5856d6'
  on-primary-container: '#e7e4ff'
  inverse-primary: '#c2c1ff'
  secondary: '#0058bc'
  on-secondary: '#ffffff'
  secondary-container: '#0070eb'
  on-secondary-container: '#fefcff'
  tertiary: '#005d20'
  on-tertiary: '#ffffff'
  tertiary-container: '#00782c'
  on-tertiary-container: '#8fff9a'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#e2dfff'
  primary-fixed-dim: '#c2c1ff'
  on-primary-fixed: '#0c006a'
  on-primary-fixed-variant: '#3631b4'
  secondary-fixed: '#d8e2ff'
  secondary-fixed-dim: '#adc6ff'
  on-secondary-fixed: '#001a41'
  on-secondary-fixed-variant: '#004493'
  tertiary-fixed: '#72fe88'
  tertiary-fixed-dim: '#53e16f'
  on-tertiary-fixed: '#002107'
  on-tertiary-fixed-variant: '#00531c'
  background: '#fcf8fb'
  on-background: '#1b1b1d'
  surface-variant: '#e4e2e4'
typography:
  large-title:
    fontFamily: Manrope
    fontSize: 34px
    fontWeight: '700'
    lineHeight: 41px
    letterSpacing: 0.37px
  large-title-mobile:
    fontFamily: Manrope
    fontSize: 30px
    fontWeight: '700'
    lineHeight: 36px
    letterSpacing: 0.35px
  title-1:
    fontFamily: Manrope
    fontSize: 28px
    fontWeight: '700'
    lineHeight: 34px
    letterSpacing: 0.36px
  title-2:
    fontFamily: Manrope
    fontSize: 22px
    fontWeight: '600'
    lineHeight: 28px
    letterSpacing: 0.35px
  title-3:
    fontFamily: Manrope
    fontSize: 20px
    fontWeight: '600'
    lineHeight: 25px
    letterSpacing: 0.38px
  headline:
    fontFamily: Hanken Grotesk
    fontSize: 17px
    fontWeight: '600'
    lineHeight: 22px
    letterSpacing: -0.41px
  body:
    fontFamily: Hanken Grotesk
    fontSize: 17px
    fontWeight: '400'
    lineHeight: 22px
    letterSpacing: -0.41px
  callout:
    fontFamily: Hanken Grotesk
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 21px
    letterSpacing: -0.32px
  subheadline:
    fontFamily: Hanken Grotesk
    fontSize: 15px
    fontWeight: '400'
    lineHeight: 20px
    letterSpacing: -0.24px
  footnote:
    fontFamily: Hanken Grotesk
    fontSize: 13px
    fontWeight: '400'
    lineHeight: 18px
    letterSpacing: -0.08px
  caption-1:
    fontFamily: Hanken Grotesk
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 16px
    letterSpacing: 0px
  caption-2:
    fontFamily: Hanken Grotesk
    fontSize: 11px
    fontWeight: '500'
    lineHeight: 13px
    letterSpacing: 0.06px
rounded:
  sm: 0.5rem
  DEFAULT: 1rem
  md: 1.5rem
  lg: 2rem
  xl: 3rem
  full: 9999px
spacing:
  gutter: 1rem
  gutter-sm: 0.75rem
  gutter-lg: 1.5rem
  margin: 1rem
  margin-sm: 0.75rem
  margin-lg: 1.5rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 0.75rem
  space-lg: 1rem
  space-xl: 1.5rem
---

## Brand & Style

This design system translates the Apple Human Interface Guidelines (HIG) into an editorial, high-precision interface language. It fuses the pragmatic rigor of iOS system conventions with Apple Pro clarity: deliberate whitespace, high-contrast typography, and fluid micro-surfaces.

The visual style embraces an elevated native iOS aesthetic:
- **Systematic Structure:** Inset grouped layouts, continuous squircle curvature, and hierarchical system backgrounds establish immediate tactile familiarity.
- **Translucent Materiality:** Vibrancy and background blurs (`UIBlurEffect` styles) define navigation bars, floating toolbars, and modal sheets rather than heavy drop shadows.
- **Editorial Typography:** High-contrast geometric headlines anchored by `Manrope` pair with the hyper-legible, crisp proportions of `Hanken Grotesk` to deliver an authoritative yet refined tone.
- **Direct Manipulation:** Affordances prioritize direct touch and optical alignment, using subtle physical transitions, active surface states, and clean system iconography.

## Colors

The color architecture implements Apple HIG semantic surface levels and vibrant system tints. The palette provides structural hierarchy through layered background tokens and restrained, high-impact accent tones.

### Semantic Roles & Palette Tokens
- **Primary Tint (`#5856D6` - System Indigo):** The key interactive accent for hero actions, highlighted badge selections, and primary active states.
- **Secondary Tint (`#007AFF` - System Blue):** Used for navigation actions, standard interactive links, segmented control pills, and action sheet triggers.
- **Tertiary Tint (`#34C759` - System Green):** Reserved for validation, success states, and the active state of `UISwitch` controls.
- **System Warning & Destructive:** System Orange (`#FF9500`) for non-blocking alerts; System Red (`#FF3B30`) strictly for destructive actions and validation errors.
- **Surfaces & Backgrounds:**
  - `systemBackground` (`#FFFFFF`): Primary surface for cards, sheets, and full-screen plain tables.
  - `secondarySystemBackground` (`#F2F2F7`): Grouped canvas backdrop behind inset cards and sectioned tables.
  - `tertiarySystemBackground` (`#E5E5EA`): Inner recessed tracks, inactive sliders, and search input fills.
- **Labels & Separators:**
  - `label` (`#000000` / `#1C1C1E`): Primary body text and headlines.
  - `secondaryLabel` (`#3C3C43` with 60% opacity / `#8E8E93`): Supporting captions, footnotes, and inactive icons.
  - `separator` (`#C6C6C8` / `rgba(60, 60, 67, 0.29)`): Hairline rules (0.5px) separating grouped table cells.

## Typography

The typography scale adheres to iOS dynamic hierarchy with calibrated optical tracking. 

`Manrope` is dedicated to prominent structural headlines (`large-title`, `title-1`, `title-2`, `title-3`), providing confident, geometric impact with balanced proportions. `Hanken Grotesk` governs all standard textual interaction (`body`, `headline`, `subheadline`, `footnote`, `caption`), maintaining strict vertical metric alignment and legible counterforms across dense lists, tables, and settings menus.

### Typographic Rules
- **Large Title Collapsing:** The `large-title` style sits prominently above safe areas on initial scroll and smoothly transforms into an inline centered `headline` style inside the navigation bar as the content ascends.
- **Section Headers:** Inset grouped headers use uppercase `footnote` or `caption-1` typography with `secondaryLabel` styling and an additional `16px` leading indent matching table content.
- **Numbers & Monospace:** Financial, quantitative, and status figures must use tabular numerals (`font-feature-settings: "tnum"`) to preserve vertical alignment across lists and metric rows.

## Layout & Spacing

Layout adheres to standard iOS viewport structures: content sits inside the device safe areas, bounded by top system status bars/navigation bars and bottom Home Indicator docks.

### Grid & Margins
- **Mobile (Base < 768px):** Single-column layout with 16px (`1rem`) outer screen margins. Inset grouped containers sit flush with 16px lateral padding. Gutter rhythm is 16px.
- **Tablet / iPad (768px - 1024px):** 2-column or split-view master-detail configuration with 20px–24px margins. Sidebar lists occupy a fixed width of 320px with the main canvas taking fluid space.
- **Desktop / Wide Layouts (> 1024px):** Maximum readable container constraints at 1180px with centered presentation, mimicking macOS or iPadOS Stage Manager presentations.

### Spacing Rhythms
- **Atomic Rhythm:** All spacing tokens scale on a 4pt/8pt grid. 
- **List Insets:** Standard grouped table cells enforce a minimum height of 44px (touch target standard) with horizontal padding of 16px (`space-lg`) and vertical padding of 11px.
- **Section Spacing:** Groups of table items are separated by a minimum vertical clearance of 24px (`1.5rem`), allowing section headers and footers to sit uncluttered in the grouped backdrop.

## Elevation & Depth

Visual hierarchy uses layered material planes, vibrancy, and delicate ambient shadows rather than harsh physical skeuomorphism.

### Material Surfaces & Glassmorphism
- **Ultra-Thin Material:** Used on top navigation bars, search headers, and the bottom tab bar. Implemented via background translucent fills (`rgba(255, 255, 255, 0.82)`) coupled with heavy hardware-accelerated blur (`backdrop-filter: blur(20px) saturate(180%)`).
- **Hairline Borders:** Translucent surfaces use a 0.5px solid border (`rgba(0, 0, 0, 0.12)` in light mode) on bottom or top edges to establish crisp physical separation without drop shadows.

### Ambient Elevation Tiers
- **Tier 0 (Canvas):** `secondarySystemBackground` (`#F2F2F7`). Completely flat base surface.
- **Tier 1 (Cards & Inset Groups):** `systemBackground` (`#FFFFFF`). No shadow; separation is achieved via the contrasting Tier 0 background and 0.5px borders where appropriate.
- **Tier 2 (Floating Modals & Popovers):** Elevated system background (`#FFFFFF`) with an ambient, diffused shadow: `0 10px 24px -4px rgba(0, 0, 0, 0.08), 0 4px 8px -2px rgba(0, 0, 0, 0.04)`.
- **Tier 3 (Action Sheets & Context Menus):** High-level overlay with backdrop dimming (`rgba(0, 0, 0, 0.35)`) and diffused soft expansion: `0 24px 48px -12px rgba(0, 0, 0, 0.18)`.

## Shapes

The design system incorporates Apple continuous squircles (`corner-curve: continuous`), avoiding abrupt mechanical circular corners.

### Radius Application
- **Pill (`rounded-full` / `9999px`):** Segmented control slider handles, floating action tags, and standalone primary action buttons.
- **Continuous Card Squircle (`rounded-2xl` / `16px`):** Inset grouped lists, grouped settings cards, and floating content sheets.
- **Inner Elements (`rounded-lg` / `10px`–`12px`): App icons, modal thumbnail previews, and inner grouped rows.
- **Controls (`rounded-md` / `8px`):** Steppers, search field inputs, and context popover items.

## Components

### Buttons & Taps
- **Primary Prominent Button:** High-tint pill or squircle (`12px` radius) filled with Primary Tint (`#5856D6`) or System Blue (`#007AFF`), white text (`headline`), min-height 48px. Features a smooth scale-down effect (`transform: scale(0.97)`) on active touch press.
- **Secondary Borderless Button:** Clear background with colored text and icon, 44px touch target, system font weight `600`.
- **Tertiary Gray Fill:** `#E5E5EA` background with system primary label color for neutral actions.

### Inset Grouped Tables & Cells
- **Container:** Rounded squircle (`16px`), background `#FFFFFF`, nested within the `#F2F2F7` base canvas.
- **Row Architecture:** 44px min-height, 16px lateral padding. Rows within a group are separated by a 0.5px hairline divider indented 16px from the leading edge (aligning with label text, skipping icon).
- **Accessories:** Standard chevron (`chevron.right`, 13px bold, secondary label color), switches, or secondary value text.

### Segmented Controls
- **Track:** 32px height, 8px corner radius, background `#E5E5EA` with 2px inner padding.
- **Active Thumb:** Pure white (`#FFFFFF`) pill with a subtle shadow (`0 2px 4px rgba(0,0,0,0.12), 0 1px 1px rgba(0,0,0,0.04)`). Text switches dynamically from secondary label to primary label color on selection.

### Form Toggles & Checkboxes
- **Toggle Switch (`UISwitch`):** 51px width, 31px height. Off state is an `#E5E5EA` track with a white circular thumb. Active state transitions to System Green (`#34C759`) background.
- **Checkboxes & Selection Lists:** Handled as grouped table rows displaying a checkmark (`checkmark`, `#5856D6` or `#007AFF`) trailing accessory when active.

### Inputs & Search Bars
- **Search Field:** 36px height, continuous 10px rounded corners, background `#767680` with 12% opacity. Preceded by a search glyph and terminated by a clear button (`xmark.circle.fill`) in secondary label tone.
- **Form Text Fields:** Embedded in grouped tables with clear labels and quiet inline borders.

### Navigation Bars & Tab Bars
- **Top Navigation Bar:** Frosted ultra-thin glass background with large title collapsing dynamically on scroll. Actions sit as borderless icon/text buttons at 17px weight.
- **Bottom Tab Bar:** 49px base height + safe area bottom inset. Frosted glass blur (`backdrop-blur-xl bg-white/80 border-t border-black/10`). Icons use 24px SF Symbols style with dual-state rendering: regular outline when unselected, solid fill when active.