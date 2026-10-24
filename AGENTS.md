# Project: LocalRP — Offline Android AI Roleplay App

Act as my Android application architect and development assistant. Help me design and build a fully functional Android roleplay application on my Artix Linux computer.

**Your job is to help me build the application, not merely explain how I could build it.** However, do not rush into executing commands or overwhelm me with unnecessary setup steps.

## 1. My hardware and environment

**Development computer**
- OS: Artix Linux, x86_64
- CPU: Intel Celeron N4020, 2 cores / 2 threads
- RAM: Approximately 3.4 GiB available to the system
- Storage: 256 GB NVMe SSD
- Shell: Bash

**Target phone**
- Device: Reeder S71
- Android: Android 14
- RAM: 12 GB
- Architecture: ARM64

**Already installed**
- OpenJDK 17
- Android SDK command-line tools
- Android SDK Platform 36
- Android Build Tools 36.0.0
- Android platform-tools / ADB
- Gradle 8.13 at `~/Android/tools/gradle-8.13/bin/gradle`

**Project directory**
`~/Projects/LocalRP`

The project directory structure has been created, but the actual application has not yet been implemented.

Do not reinstall these tools unless you identify a specific problem that requires it. Do not install Android Studio, an Android emulator, or Flatpak.

## 2. Application vision

LocalRP is a mobile AI roleplay application focused on custom characters and multi-character group roleplay.

The application should prioritize:
- Local, offline AI inference.
- Character creation and customization.
- Group conversations involving multiple characters.
- Persistent chat history.
- Low resource consumption.
- User control over models and settings.
- Optional online AI providers in a later version.

The application must be usable without an internet connection once the required model has been installed.

## 3. Required features

### Character management
- Create, edit, and delete characters.
- Set a character's name, description, personality, and system instructions.
- Support example dialogues and optional character information fields.
- Save characters locally.
- Import and export character data where practical.

### Group roleplay
- Create group conversations.
- Add multiple custom characters to a group.
- Give every character separate instructions and personality traits.
- Allow manual selection of the next speaker.
- Eventually support automatic speaker selection.
- Maintain coherent shared conversation context.
- Prevent uncontrolled generation loops.

### Chat interface
- Display messages with clear speaker names.
- Support user and character messages.
- Provide a message input field and send button.
- Allow generation to be stopped.
- Preserve conversation history.
- Include basic generation settings, such as temperature and context length, when supported by the inference engine.

### Local AI
- Prefer GGUF models and `llama.cpp` if they are suitable for Android ARM64.
- Keep model files separate from the APK where practical.
- Allow the user to select and manage compatible model files.
- Test actual inference performance on the target phone.
- Choose a model based on real memory, speed, and quality constraints instead of assuming that 12 GB of RAM guarantees compatibility with every model.

### Data and privacy
- Store conversations and characters locally.
- Make offline operation the default.
- Do not transmit conversation data without explicit user action.
- Do not embed private API keys in the APK.
- Provide a practical backup and restore mechanism.

## 4. Development roadmap

Follow these phases in order:

**Phase 1 — Working application foundation**
Build a native Android application with:
- A home screen.
- Character list and character creation form.
- Group creation and character selection.
- A functional chat interface.
- Sample responses or a clearly identified temporary mock mode.
- Basic navigation.

The goal is to produce, install, and launch a working APK on the actual phone.

**Phase 2 — Persistent storage**
Implement local character and conversation storage, editing, deletion, and basic backup/restore.

**Phase 3 — Local AI inference**
Integrate `llama.cpp` or another justified local inference engine. Test a suitable GGUF model on the actual device. Measure performance and memory usage.

**Phase 4 — Group AI**
Implement individual character prompts, shared context, speaker selection, and reliable generation control.

**Phase 5 — Optional online providers**
Add configurable online inference providers without making them mandatory for offline functionality. Keep credentials secure and explain any additional requirements.

## 5. Technical constraints

- Keep the application lightweight.
- Prefer native Android components and minimal dependencies.
- Use the existing Java 17 and Android SDK installation.
- Verify compatibility between the Android Gradle Plugin, Gradle, Java, and the selected Android SDK.
- Do not assume that the system-installed tools or project configuration are correct without checking relevant details.
- Avoid unnecessary downloads, upgrades, and configuration changes.
- Do not build an entire application in one enormous, difficult-to-debug step.
- Do not add complex architecture or dependencies without a concrete reason.

## 6. How you should work with me

I am the project owner, not an unattended terminal operator.

Follow these rules:

1. Explain the purpose of a major decision briefly and clearly.
2. Do not give me a long sequence of unrelated terminal commands.
3. Do not repeatedly ask me to run diagnostic commands when the necessary information is already available.
4. Inspect existing project files before creating or overwriting them.
5. If you have terminal access, perform routine file creation, implementation, builds, and tests yourself rather than making me copy every command manually.
6. If you do not have terminal access, provide only the next meaningful step and explain what it accomplishes.
7. Never claim a build or test succeeded unless you actually verified the result.
8. When a failure occurs, investigate its cause before proposing another command.
9. Avoid changing system-wide settings or using elevated privileges unless necessary.
10. Preserve working code and avoid destructive changes.
11. Ask questions only when a decision materially affects the implementation. Otherwise, make a reasonable choice and explain it.
12. Keep me informed without turning every minor action into a lengthy report.

## 7. Definition of success

The initial milestone is complete when:
- A debug APK builds successfully.
- The APK installs and launches on my Android 14 phone.
- Character creation works.
- Group creation and character selection work.
- The chat interface accepts and displays messages.
- Data persists after restarting the application.
- The basic application works offline.

A mock response is acceptable only for the initial UI milestone. Do not represent it as real AI inference.

## Your first task

Review the information above and determine the most appropriate next step based on the current state of the project.

Do not immediately give me a long list of commands. First identify what is already ready, what is missing, and what the smallest useful implementation step should be. Then proceed methodically toward the first working APK.
