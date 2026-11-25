Unicode true
!include "MUI2.nsh"
!include "FileFunc.nsh"
!include "StrFunc.nsh"
${StrStr}
${UnStrStr}
Name "B+ Media Player 1.0.11"
OutFile "../dist/Bplus-Media-Player-1.0.11-Setup.exe"
InstallDir "$LOCALAPPDATA\Programs\Bplus Software\bplus media player"
InstallDirRegKey HKCU "Software\Bplus" "InstallLocation"
RequestExecutionLevel user
SetCompressor /SOLID zlib
VIProductVersion "1.0.11.0"
VIAddVersionKey "ProductName" "B+ Media Player"
VIAddVersionKey "FileDescription" "B+ per-user installer"
VIAddVersionKey "FileVersion" "1.0.11"
VIAddVersionKey "LegalCopyright" "B+ contributors"
!define MUI_ABORTWARNING
!define MUI_FINISHPAGE_RUN "$INSTDIR\B+.exe"
!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH
!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES
!insertmacro MUI_LANGUAGE "English"

 ; Stable upgrade identity: HKCU uninstall key Bplus. No versioned key or MSI GUID.
Var OLDDIR
Function .onInit
 SetShellVarContext current
 ; v1.5 and later record this value in both stable locations.
 ReadRegStr $0 HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Bplus" "InstallLocation"
 StrCmp $0 "" legacy_path
 IfFileExists "$0\B+.exe" 0 legacy_path
 StrCpy $INSTDIR $0
 Goto path_ready
 legacy_path:
 ; v1.2-v1.4 used the same fixed folder but did not record InstallLocation.
 ; Recover only our exact known uninstaller filename. Do not execute registry text.
 ReadRegStr $0 HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Bplus" "UninstallString"
 StrCmp $0 "" path_ready
 StrCpy $1 $0 1
 StrCmp $1 '$\"' 0 unquoted_path
 StrCpy $0 $0 -1 1
 unquoted_path:
 ${GetFileName} "$0" $1
 StrCmp $1 "Uninstall Bplus.exe" 0 path_ready
 ${GetParent} "$0" $1
 IfFileExists "$1\B+.exe" 0 path_ready
 StrCpy $INSTDIR $1
 path_ready:
 ; Move an install from the old default folder into the Bplus Software parent. Custom folders stay where they are.
 StrCpy $OLDDIR ""
 StrCmp $INSTDIR "$LOCALAPPDATA\Programs\Bplus" 0 move_done
 IfFileExists "$INSTDIR\Uninstall Bplus.exe" 0 move_done
 StrCpy $OLDDIR $INSTDIR
 StrCpy $INSTDIR "$LOCALAPPDATA\Programs\Bplus Software\bplus media player"
 move_done:
FunctionEnd

!macro RegisterType EXT
 WriteRegStr HKCU "Software\Classes\Bplus.${EXT}" "" "B+ ${EXT} media"
 WriteRegStr HKCU "Software\Classes\Bplus.${EXT}\DefaultIcon" "" '$\"$INSTDIR\B+.exe$\",0'
 WriteRegStr HKCU "Software\Classes\Bplus.${EXT}\shell\open\command" "" '$\"$INSTDIR\B+.exe$\" -- $\"%1$\"'
 DeleteRegKey HKCU "Software\Classes\Bplus.${EXT}\shell\open\ddeexec"
 WriteRegStr HKCU "Software\Bplus\Capabilities\FileAssociations" ".${EXT}" "Bplus.${EXT}"
 WriteRegNone HKCU "Software\Classes\.${EXT}\OpenWithProgids" "Bplus.${EXT}"
 WriteRegStr HKCU "Software\Classes\Applications\B+.exe\SupportedTypes" ".${EXT}" ""
!macroend
!macro RemoveType EXT
 DeleteRegKey HKCU "Software\Classes\Bplus.${EXT}"
 DeleteRegValue HKCU "Software\Classes\.${EXT}\OpenWithProgids" "Bplus.${EXT}"
!macroend

!macro EnsureClosed SUFFIX
 retry_${SUFFIX}:
 ; Include portable copies: they also run as B+.exe outside the install folder.
 ; If tasklist is unavailable, retain the executable-lock check below.
 nsExec::ExecToStack /TIMEOUT=5000 '$\"$SYSDIR\tasklist.exe$\" /FI $\"IMAGENAME eq B+.exe$\" /NH /FO CSV'
 Pop $1
 Pop $2
 StrCmp $1 "0" 0 filecheck_${SUFFIX}
 !if "${SUFFIX}" == "uninstall"
  ${UnStrStr} $3 $2 '$\"B+.exe$\"'
 !else
  ${StrStr} $3 $2 '$\"B+.exe$\"'
 !endif
 StrCmp $3 "" filecheck_${SUFFIX} busy_${SUFFIX}
 filecheck_${SUFFIX}:
 IfFileExists "$INSTDIR\B+.exe" 0 closed_${SUFFIX}
 ClearErrors
 FileOpen $0 "$INSTDIR\B+.exe" a
 IfErrors busy_${SUFFIX}
 FileClose $0
 Goto closed_${SUFFIX}
 busy_${SUFFIX}:
 MessageBox MB_RETRYCANCEL|MB_ICONEXCLAMATION "Close every B+ window, including portable copies, before continuing. If B+ is closed, check that this installation folder is writable." /SD IDCANCEL IDRETRY retry_${SUFFIX}
 SetErrorLevel 2
 Abort
 closed_${SUFFIX}:
!macroend

Section "B+"
 !insertmacro EnsureClosed install
 SetShellVarContext current
 SetOutPath "$INSTDIR"
 WriteRegStr HKCU "Software\Bplus\Capabilities" "ApplicationName" "B+ Media Player"
 WriteRegStr HKCU "Software\Bplus\Capabilities" "ApplicationDescription" "Offline video and audio player"
 WriteRegStr HKCU "Software\Bplus\Capabilities" "ApplicationIcon" '$\"$INSTDIR\B+.exe$\",0'
 WriteRegStr HKCU "Software\RegisteredApplications" "Bplus" "Software\Bplus\Capabilities"
 File /r "../dist/win-unpacked/*"
 ; v1.7 feature removed: delete bundled whisper engine and any downloaded model.
 RMDir /r "$INSTDIR\resources\whisper"
 RMDir /r "$APPDATA\bplus-player\whisper-models"
 WriteUninstaller "$INSTDIR\Uninstall Bplus.exe"
 StrCmp $OLDDIR "" +2
 RMDir /r "$OLDDIR"
 WriteRegStr HKCU "Software\Bplus" "InstallLocation" "$INSTDIR"
 WriteRegStr HKCU "Software\Bplus" "AppId" "com.bplus.player"
 WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Bplus" "InstallLocation" "$INSTDIR"
 WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Bplus" "AppId" "com.bplus.player"
 ; Rename from v1.5 and earlier: remove old shortcut names, keep one entry per location.
 Delete "$DESKTOP\B+.lnk"
 Delete "$SMPROGRAMS\B+\B+.lnk"
 RMDir "$SMPROGRAMS\B+"
 Delete "$DESKTOP\B+ Media Player.lnk"
 Delete "$SMPROGRAMS\B+ Media Player\B+ Media Player.lnk"
 RMDir "$SMPROGRAMS\B+ Media Player"
 CreateDirectory "$SMPROGRAMS\B+ Media Player"
 CreateShortcut "$SMPROGRAMS\B+ Media Player\B+ Media Player.lnk" "$INSTDIR\B+.exe"
 CreateShortcut "$DESKTOP\B+ Media Player.lnk" "$INSTDIR\B+.exe"
 WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Bplus" "DisplayName" "B+ Media Player"
 WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Bplus" "DisplayVersion" "1.0.11"
 WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Bplus" "UninstallString" '$\"$INSTDIR\Uninstall Bplus.exe$\"'
 WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Bplus" "DisplayIcon" "$INSTDIR\B+.exe"
 WriteRegDWORD HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Bplus" "NoModify" 1
 WriteRegDWORD HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Bplus" "NoRepair" 1
 WriteRegStr HKCU "Software\Classes\Applications\B+.exe" "FriendlyAppName" "B+ Media Player"
 WriteRegStr HKCU "Software\Classes\Applications\B+.exe\shell\open\command" "" '$\"$INSTDIR\B+.exe$\" -- $\"%1$\"'
 !insertmacro RegisterType mp4
 !insertmacro RegisterType m4v
 !insertmacro RegisterType mov
 !insertmacro RegisterType webm
 !insertmacro RegisterType ogv
 !insertmacro RegisterType ogg
 !insertmacro RegisterType mp3
 !insertmacro RegisterType flac
 !insertmacro RegisterType wav
 !insertmacro RegisterType aac
 !insertmacro RegisterType mkv
 !insertmacro RegisterType avi
 !insertmacro RegisterType wmv
 !insertmacro RegisterType flv
 !insertmacro RegisterType m4a
 !insertmacro RegisterType wma
 !insertmacro RegisterType mpeg
 !insertmacro RegisterType mpg
 !insertmacro RegisterType m2v
 !insertmacro RegisterType mts
 !insertmacro RegisterType m2ts
 !insertmacro RegisterType ts
 !insertmacro RegisterType vob
 !insertmacro RegisterType 3gp
 !insertmacro RegisterType 3g2
 !insertmacro RegisterType asf
 !insertmacro RegisterType divx
 !insertmacro RegisterType f4v
 !insertmacro RegisterType opus
 !insertmacro RegisterType aiff
 !insertmacro RegisterType aif
 !insertmacro RegisterType ape
 !insertmacro RegisterType ac3
 !insertmacro RegisterType dts
 !insertmacro RegisterType amr
 !insertmacro RegisterType alac
 !insertmacro RegisterType mka
 !insertmacro RegisterType mp2
 !insertmacro RegisterType oga
 !insertmacro RegisterType weba
 System::Call 'shell32::SHChangeNotify(i 0x08000000, i 0, p 0, p 0)'
SectionEnd
Section "Uninstall"
 !insertmacro EnsureClosed uninstall
 SetShellVarContext current
 Delete "$DESKTOP\B+.lnk"
 Delete "$SMPROGRAMS\B+\B+.lnk"
 RMDir "$SMPROGRAMS\B+"
 Delete "$DESKTOP\B+ Media Player.lnk"
 Delete "$SMPROGRAMS\B+ Media Player\B+ Media Player.lnk"
 RMDir "$SMPROGRAMS\B+ Media Player"
 !insertmacro RemoveType mp4
 !insertmacro RemoveType m4v
 !insertmacro RemoveType mov
 !insertmacro RemoveType webm
 !insertmacro RemoveType ogv
 !insertmacro RemoveType ogg
 !insertmacro RemoveType mp3
 !insertmacro RemoveType flac
 !insertmacro RemoveType wav
 !insertmacro RemoveType aac
 !insertmacro RemoveType mkv
 !insertmacro RemoveType avi
 !insertmacro RemoveType wmv
 !insertmacro RemoveType flv
 !insertmacro RemoveType m4a
 !insertmacro RemoveType wma
 !insertmacro RemoveType mpeg
 !insertmacro RemoveType mpg
 !insertmacro RemoveType m2v
 !insertmacro RemoveType mts
 !insertmacro RemoveType m2ts
 !insertmacro RemoveType ts
 !insertmacro RemoveType vob
 !insertmacro RemoveType 3gp
 !insertmacro RemoveType 3g2
 !insertmacro RemoveType asf
 !insertmacro RemoveType divx
 !insertmacro RemoveType f4v
 !insertmacro RemoveType opus
 !insertmacro RemoveType aiff
 !insertmacro RemoveType aif
 !insertmacro RemoveType ape
 !insertmacro RemoveType ac3
 !insertmacro RemoveType dts
 !insertmacro RemoveType amr
 !insertmacro RemoveType alac
 !insertmacro RemoveType mka
 !insertmacro RemoveType mp2
 !insertmacro RemoveType oga
 !insertmacro RemoveType weba
 DeleteRegValue HKCU "Software\Bplus" "InstallLocation"
 DeleteRegValue HKCU "Software\Bplus" "AppId"
 DeleteRegKey HKCU "Software\Bplus\Capabilities"
 DeleteRegValue HKCU "Software\RegisteredApplications" "Bplus"
 DeleteRegKey HKCU "Software\Classes\Applications\B+.exe"
 DeleteRegKey HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Bplus"
 RMDir /r "$INSTDIR"
 ; Shared parent for all B+ programs: removed only when empty (RMDir without /r).
 RMDir "$LOCALAPPDATA\Programs\Bplus Software"
 System::Call 'shell32::SHChangeNotify(i 0x08000000, i 0, p 0, p 0)'
SectionEnd
