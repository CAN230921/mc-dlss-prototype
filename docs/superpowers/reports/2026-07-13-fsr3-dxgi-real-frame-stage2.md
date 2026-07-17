# FSR3 DXGI Real-Frame Presentation: Stage 2

## Status

Implementation and automated verification are complete. Minecraft startup and
Mixin injection passed. The FSR3 session acceptance run is pending because
Windows Application Control blocks the newly built DLL when Minecraft is
started from the Codex process chain.

## Implemented

- A native same-HWND D3D12/DXGI presenter owned by the live FSR3 session.
- Direct copy from the completed HDR FSR3 output to an HDR DXGI back buffer.
- Explicit OpenGL-only, warming, active, and fallback states.
- OpenGL swap suppression only after a previous native present succeeded.
- A redirect of only `GLFW.glfwSwapBuffers` inside `RenderSystem.flipFrame`.
- Bounded queue draining during fallback and session destruction.
- A developer-only activation property.

## Verification

- Native Release build: passed.
- NeoForge Java and Mixin compilation: passed.
- FSR3/native focused CTest suite: 9/9 passed.
- Experimental distribution JAR: built and inspected.

## Runtime Gate

Use `-DmcDlss.experimentalDxgiPresentation=true` for Stage 2 testing. The
NeoForge Gradle development client adds it automatically. The packaged JAR does
not activate takeover without this property.

Stage 2 presents the real FSR3 output. Official FidelityFX generated-frame
scheduling and shared UI composition remain Stage 3 and Stage 4 work.

For the user-session acceptance run, launch `work/run-fsr3-dxgi-stage2.bat`
interactively and enter the existing test world. The saved development config
already selects FSR3, enables super resolution, and requests frame generation.
