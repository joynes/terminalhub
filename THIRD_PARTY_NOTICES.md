# Third-Party Notices

This project includes or adapts code, assets, and libraries from the following
projects. Unless otherwise noted, TerminalHub's own source code is distributed
under GPLv3 only as described in `LICENSE`.

## Termux

TerminalHub includes Termux terminal integration code and terminal rendering/input components adapted from the Termux project:

- https://github.com/termux/termux-app

The Termux app repository is released under GPLv3 only. Its upstream license notes state exceptions for Terminal Emulator for Android code in the `terminal-view` and `terminal-emulator` libraries under Apache License 2.0.

TerminalHub is distributed under GPLv3 only to satisfy redistribution requirements for GPL-covered Termux-derived code.

## Terminal Emulator for Android

The `terminal-view` and `terminal-emulator` modules contain terminal code derived from Terminal Emulator for Android / Termux terminal libraries. Upstream Termux documents these libraries as Apache License 2.0 exceptions.

## Android Open Source Project

Some compatibility code is derived from Android Open Source Project examples or support-library code and is used under the Apache License 2.0 where noted in source headers.

## xterm.js

The web terminal assets under `app/src/main/assets/terminal/` include xterm.js-related files. xterm.js is distributed under the MIT License by the xterm.js authors.

MIT License text for xterm.js:

Copyright (c) 2014 The xterm.js authors. All rights reserved.
Copyright (c) 2012-2013, Christopher Jeffrey.

Permission is hereby granted, free of charge, to any person obtaining a copy of
this software and associated documentation files (the "Software"), to deal in
the Software without restriction, including without limitation the rights to use,
copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
Software, and to permit persons to whom the Software is furnished to do so,
subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

## Runtime Libraries

The Android application also depends on third-party libraries resolved by
Gradle. Their licenses are not relicensed by this project; they remain under
their upstream terms. The main runtime dependencies are:

- AndroidX Core, Activity, Lifecycle, Navigation, Biometric, Security Crypto,
  Compose UI/Foundation/Material: Apache License 2.0.
- Kotlin coroutines: Apache License 2.0.
- Dagger/Hilt: Apache License 2.0.
- Room: Apache License 2.0.
- ConnectBot `sshlib`: Apache License 2.0.
- mwiede JSch: BSD-style license.

## Test Libraries

The test-only dependencies include:

- JUnit: Eclipse Public License 1.0.
- Mockito and Mockito-Kotlin: MIT License.
- AndroidX Test, Espresso, UI Automator, Compose test artifacts: Apache License
  2.0.

## Source Availability

TerminalHub's corresponding source is the contents of this repository. Build
instructions are in `README.md`.
