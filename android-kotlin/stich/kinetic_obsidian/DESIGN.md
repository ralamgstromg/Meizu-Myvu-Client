---
name: Kinetic Obsidian
colors:
  surface: '#121416'
  surface-dim: '#121416'
  surface-bright: '#37393b'
  surface-container-lowest: '#0c0e10'
  surface-container-low: '#1a1c1e'
  surface-container: '#1e2022'
  surface-container-high: '#282a2c'
  surface-container-highest: '#333537'
  on-surface: '#e2e2e5'
  on-surface-variant: '#b9cacb'
  inverse-surface: '#e2e2e5'
  inverse-on-surface: '#2f3133'
  outline: '#849495'
  outline-variant: '#3b494b'
  surface-tint: '#00dbe9'
  primary: '#dbfcff'
  on-primary: '#00363a'
  primary-container: '#00f0ff'
  on-primary-container: '#006970'
  inverse-primary: '#006970'
  secondary: '#bac9cd'
  on-secondary: '#253336'
  secondary-container: '#3e4b4f'
  on-secondary-container: '#acbbbf'
  tertiary: '#fcf2ff'
  on-tertiary: '#460283'
  tertiary-container: '#e7d0ff'
  on-tertiary-container: '#7743b5'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#7df4ff'
  primary-fixed-dim: '#00dbe9'
  on-primary-fixed: '#002022'
  on-primary-fixed-variant: '#004f54'
  secondary-fixed: '#d6e5e9'
  secondary-fixed-dim: '#bac9cd'
  on-secondary-fixed: '#101e21'
  on-secondary-fixed-variant: '#3c494d'
  tertiary-fixed: '#eedbff'
  tertiary-fixed-dim: '#dab9ff'
  on-tertiary-fixed: '#2a0053'
  on-tertiary-fixed-variant: '#5e289b'
  background: '#121416'
  on-background: '#e2e2e5'
  surface-variant: '#333537'
typography:
  display-lg:
    fontFamily: Hanken Grotesk
    fontSize: 57px
    fontWeight: '700'
    lineHeight: 64px
    letterSpacing: -0.25px
  headline-lg:
    fontFamily: Hanken Grotesk
    fontSize: 32px
    fontWeight: '600'
    lineHeight: 40px
  headline-sm:
    fontFamily: Hanken Grotesk
    fontSize: 24px
    fontWeight: '500'
    lineHeight: 32px
  body-lg:
    fontFamily: Hanken Grotesk
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
    letterSpacing: 0.5px
  body-md:
    fontFamily: Hanken Grotesk
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
    letterSpacing: 0.25px
  label-md:
    fontFamily: JetBrains Mono
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 16px
    letterSpacing: 0.5px
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  base: 8px
  container-padding: 24px
  gutter: 16px
  stack-sm: 4px
  stack-md: 12px
  stack-lg: 24px
---

## Brand & Style

The design system is engineered for high-performance utility with a futuristic edge. It bridges the gap between a first-party system tool and an advanced AI interface. The personality is precise, intelligent, and unobtrusive, ensuring the focus remains on the augmented reality experience provided by the hardware.

The visual style is a refined take on **Modern Minimalism** mixed with **Tonal Layering**. It leverages deep charcoal backgrounds to simulate the "off" state of a lens, with vibrant highlights that represent the "on" state of data and AI connectivity. The emotional response is one of total control and seamless integration.

## Colors

The palette is anchored in a "Dark Mode First" philosophy to mirror the hardware's aesthetic.

- **Primary (Cyber Teal):** Used for critical actions, AI status indicators, and active connectivity states. It should feel like it is emitting light.
- **Surface Strategy:** This design system utilizes Material 3 Tonal Palettes. 
    - `surface-container-low` is used for the primary background to provide a deep, immersive base.
    - `surface-container-high` is used for interactive cards and elevated sections.
- **Neutral:** A range of deep charcoals and pure whites (for text) ensures high legibility and a premium, high-tech finish.

## Typography

The typography system uses **Hanken Grotesk** for its sharp, contemporary geometry and exceptional legibility in data-dense environments. To lean into the "AI/System Utility" aesthetic, **JetBrains Mono** is utilized for labels, status indicators, and technical readouts, providing a precise, monospaced contrast to the fluid sans-serif headlines.

Large headlines should be reserved for device status (e.g., "Glasses Connected"), while body text remains clean and spacious to avoid cognitive load.

## Layout & Spacing

This design system follows a **12-column fluid grid** for desktop/tablet and a **4-column fluid grid** for mobile. 

- **Margins:** A generous 24px outer margin ensures the UI feels "airy" and premium.
- **Rhythm:** An 8px linear scale governs all spatial relationships. 
- **Touch Targets:** Minimum interactive area is 48x48dp, but for primary hardware controls (like "Power" or "Calibrate"), targets should be expanded to 64dp height for accessibility and ease of use on the go.

## Elevation & Depth

Hierarchy is established through **Tonal Layers** rather than heavy shadows. 

- **Base Layer:** `surface-container-low` (Deep charcoal).
- **Interactive Layer:** `surface-container-high` (Slightly lighter charcoal) with a 1px "ghost border" (low-opacity white) to define the edge.
- **Active State:** The primary color is used as a subtle "glow" or outer glow effect (blur: 8px, opacity: 20%) to indicate an active AI process or connectivity stream.
- **Glassmorphism:** Use background blurs (32px radius) on top-level navigation bars and sticky headers to maintain a sense of environmental awareness.

## Shapes

The shape language follows a strict hierarchy of rounding to distinguish between hardware containers and software controls:

- **Large Containers (Cards, Modals):** 28px corner radius. This creates a soft, modern look that contrasts with the technical nature of the app.
- **Medium Elements (Buttons, Inputs):** 16px corner radius.
- **Small Elements (Chips, Checkboxes):** 8px corner radius.
- **AI Orbs/Indicators:** Always 100% circular to represent the "fluidity" of intelligence.

## Components

- **Buttons:** 
  - *Primary:* Filled with Cyber Teal, black text for high contrast. 
  - *Secondary:* Outlined with a 1px border of the Primary color, no fill.
- **Chips:** Used for filtering AI notification categories. These should use the `label-md` JetBrains Mono font for a technical feel.
- **Input Fields:** Flat filled style using `surface-container-high` with a 2px Cyber Teal bottom indicator when focused.
- **Connectivity Cards:** Large cards (28px radius) featuring a glyph of the smart glasses. Use a pulsing animation on the Primary color border to indicate an active Bluetooth pairing process.
- **Status Indicators:** Use small, high-vibrancy "LED-style" dots. Green for 100% battery, Cyber Teal for active AI, and Amber for firmware updates.
- **AI Interaction Bar:** A persistent floating pill-shaped element at the bottom of the screen (rounded-xl) for quick voice commands or AI query input.