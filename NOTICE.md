# Notices

- **This addon**: Eclipse Public License 1.0 (https://www.eclipse.org/legal/epl-v10.html).
- **Eclipse Color Theme** (https://github.com/eclipse-color-theme/eclipse-color-theme, EPL-1.0): the theme XML format and the idea of per-editor key mappings come from this plug-in. The mapping tables in `tools/gen_mappings.py` were written anew for this addon and compared key by key with its mapping files on 2026-11-14 (all shared keys now use the same palette key; `occurrenceAnnotation` is left out); no code of it is included. The WST JSON editor mapping is new.
- **Bundled themes**: `io.github.jollygood1.syntaxthemes/themes/` holds, besides `tomorrow-night.xml`, 26 theme files taken unchanged from the Eclipse Color Theme plug-in (folder `com.github.eclipsecolortheme/themes`, EPL-1.0); `notepad++-like.xml` was renamed to `notepad-plus-plus-like.xml`. Authors: see the table "Theme authors" below. The page in Eclipse shows the author of the selected theme.
- **Tomorrow Night** colours: Chris Kempson (https://github.com/chriskempson/tomorrow-theme, MIT). The bundled palette changes the background to `#1C1F22` and leaves out the style attributes of the original file (all of them "false"), so that applying it changes colours only.
- **DevStyle**: nothing from DevStyle is included.

## File Icons for Web

- **Devicon** 2.16.0, MIT License, https://github.com/devicons/devicon (language and tool logos). The logos themselves are trademarks of their respective owners.
- **Material Design Icons** (Pictogrammers) @mdi/svg 7.4.47, Apache License 2.0, https://github.com/Templarian/MaterialDesign (json, yaml, xml, config, dotenv, batch, diff, make, sql), tinted with Tomorrow Night colours.
- The licence texts of both sources and a list of which icon comes from where are in `io.github.jollygood1.fileicons/icons/web/licenses/` and are packed into the plug-in jar.
- The PNG files were rendered from the SVG sources at 16 and 32 pixels; dark logo colours were lightened so they stay visible on a dark background.
- Icons marked "Eclipse" in the icon picker are read from the running Eclipse and are not part of this repository.

## Theme authors

The themes are the work of the people below. This table is generated from the theme files by `tools/gen_theme_credits.py`.

<!-- theme credits: begin -->

| Theme | Author | Website | File |
|---|---|---|---|
| Black Pastel | David |  | `black-pastel.xml` |
| frontenddev | Plebe |  | `frontenddev.xml` |
| Gedit Original Oblivion | Sepehr Lajevardi | http://sepehr.ws/ | `gedit-original-oblivion.xml` |
| Havenjark | Rodrigo Franco | https://github.com/caffo | `havenjark.xml` |
| Inkpot | Ciaran McCreesh | http://www.vim.org/scripts/script.php?script_id=1143 | `inkpot.xml` |
| minimal | meers davy |  | `minimal.xml` |
| Monokai | Truong Xuan Tinh | http://www.tinhtruong.info | `monokai.xml` |
| Mr | Jongosi | http://TwinCreations.co.uk | `mr.xml` |
| NightLion Aptana Theme | NightLion | http://www.nightlion.net/themes/2009/aptana-dark-color-theme/ | `nightlion-aptana-theme.xml` |
| Notepad++ Like | Vokiel |  | `notepad-plus-plus-like.xml` |
| Oblivion | Roger Dudler | http://www.rogerdudler.com/?p=362 | `oblivion.xml` |
| Obsidian | Morinar |  | `obsidian.xml` |
| Pastel | Ian Kabeary | http://iank.ca | `pastel.xml` |
| RecognEyes | Dan |  | `recogneyes.xml` |
| Retta | Eric |  | `retta.xml` |
| Roboticket | Robopuff | http://robopuff.com/ | `roboticket.xml` |
| Schuss | Vasil Stoychev |  | `schuss.xml` |
| Solarized Dark | Ethan Schoonover, Miguel Cervantes | http://ethanschoonover.com/solarized | `solarized-dark.xml` |
| Solarized Light | Ethan Schoonover, George | http://ethanschoonover.com/solarized | `solarized-light.xml` |
| Sublime Text 2 | Filip Minev |  | `sublime-text-2.xml` |
| Sublime Text Monokai Extended | Andreas Guther |  | `sublime-text-monokai-extended.xml` |
| Sunburst | Viorel Craescu | http://syntetik.com | `sunburst.xml` |
| Tango | Roger Dudler |  | `tango.xml` |
| Tomorrow Night | Chris Kempson | http://github.com/ChrisKempson/Tomorrow-Theme | `tomorrow-night.xml` |
| Vibrant Ink | indiehead | http://code.google.com/p/vibrant-ink-textmate/ | `vibrantink.xml` |
| Wombat | Lars H. Nielsen | http://dengmao.wordpress.com/2007/01/22/vim-color-scheme-wombat/ | `wombat.xml` |
| Zenburn | Janni Nurminen | http://slinky.imukuppi.org/2006/10/31/just-some-alien-fruit-salad-to-keep-you-in-the-zone/ | `zenburn.xml` |

<!-- theme credits: end -->
