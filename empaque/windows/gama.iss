; Instalador de GAMA Suite para Windows (Inno Setup 6.3 o superior).
;   iscc /DVersion=1.2.3 /DClavePublica=<hex> empaque\windows\gama.iss
; Espera en dist\: gama-servidor\ (PyInstaller), pgsql\ (PostgreSQL), winsw\gama-servicio.exe y apk\*.apk.
; Parámetros para instalación desatendida: /VERYSILENT /EMPRESA="Mi Empresa C.A." /PUERTO=8443 /LICENCIA="C:\ruta\licencia.json"

#ifndef Version
  #define Version "0.0.0"
#endif
#ifndef ClavePublica
  #define ClavePublica ""
#endif
#define Dist "..\..\dist"

[Setup]
AppId={{9E6B3B1C-6C2D-4D8E-9C0A-2F6E7B1D4A55}
AppName=GAMA Suite
AppVersion={#Version}
AppVerName=GAMA Suite {#Version}
AppPublisher=GAMA Suite
VersionInfoVersion={#Version}
VersionInfoProductName=GAMA Suite
VersionInfoDescription=Instalador de GAMA Suite
DefaultDirName={autopf}\GAMA Suite
DisableProgramGroupPage=yes
OutputDir={#Dist}\instalador
OutputBaseFilename=GAMA-Suite-Setup-{#Version}
SetupIconFile=gama.ico
UninstallDisplayIcon={app}\servidor\gama-servidor.exe
UninstallDisplayName=GAMA Suite
WizardStyle=modern
WizardImageFile=asistente.bmp,asistente-2x.bmp
WizardSmallImageFile=asistente-chico.bmp,asistente-chico-2x.bmp
Compression=lzma2/max
SolidCompression=yes
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
PrivilegesRequired=admin
MinVersion=10.0.17763
LicenseFile=..\..\LICENSE
CloseApplications=no
SetupLogging=yes

[Languages]
Name: "es"; MessagesFile: "compiler:Languages\Spanish.isl"

[Files]
Source: "{#Dist}\gama-servidor\*"; DestDir: "{app}\servidor"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "{#Dist}\pgsql\*"; DestDir: "{app}\pgsql"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "{#Dist}\winsw\gama-servicio.exe"; DestDir: "{app}\servicio"; Flags: ignoreversion
Source: "{#Dist}\apk\*.apk"; DestDir: "{app}\apk"; Flags: ignoreversion skipifsourcedoesntexist
Source: "instalar.ps1"; DestDir: "{app}\scripts"; Flags: ignoreversion
Source: "desinstalar.ps1"; DestDir: "{app}\scripts"; Flags: ignoreversion

[INI]
Filename: "{autoprograms}\GAMA Suite\Consola de GAMA Suite.url"; Section: "InternetShortcut"; Key: "URL"; String: "https://localhost:{code:PuertoActual}/consola/"

[UninstallDelete]
Type: files; Name: "{autoprograms}\GAMA Suite\Consola de GAMA Suite.url"
Type: dirifempty; Name: "{autoprograms}\GAMA Suite"
Type: filesandordirs; Name: "{app}\servicio"

[Run]
Filename: "https://localhost:{code:PuertoActual}/consola/"; Description: "Abrir la consola de GAMA Suite"; Flags: postinstall shellexec nowait skipifsilent

[UninstallRun]
Filename: "{sys}\WindowsPowerShell\v1.0\powershell.exe"; Parameters: "-NoProfile -ExecutionPolicy Bypass -File ""{app}\scripts\desinstalar.ps1"" -App ""{app}"""; Flags: runhidden waituntilterminated; RunOnceId: "QuitarServicios"

[Code]
var
  PaginaEmpresa: TInputQueryWizardPage;
  PaginaLicencia: TInputFileWizardPage;
  ConfiguracionFallida: Boolean;
  EraActualizacion: Boolean;

function CarpetaDatos: String;
begin
  Result := ExpandConstant('{commonappdata}\GAMA');
end;

function EsActualizacion: Boolean;
begin
  Result := FileExists(CarpetaDatos + '\gama.env');
end;

{ Puerto de una instalación existente (línea GAMA_PUERTO=... de gama.env) o el elegido en el asistente. }
function PuertoActual(Param: String): String;
var
  Lineas: TArrayOfString;
  I: Integer;
begin
  Result := '';
  if EsActualizacion and LoadStringsFromFile(CarpetaDatos + '\gama.env', Lineas) then
    for I := 0 to GetArrayLength(Lineas) - 1 do
      if Pos('GAMA_PUERTO=', Lineas[I]) = 1 then
        Result := Trim(Copy(Lineas[I], 13, 10));
  if Result = '' then
    Result := Trim(PaginaEmpresa.Values[1]);
end;

procedure InitializeWizard;
begin
  EraActualizacion := EsActualizacion;
  PaginaEmpresa := CreateInputQueryPage(wpSelectDir,
    'Datos de la empresa', 'Con estos datos se crea la base de GAMA Suite.',
    'Escriba el nombre de la empresa y el puerto de la conexión segura (HTTPS) que usarán las tabletas.');
  PaginaEmpresa.Add('Nombre de la empresa:', False);
  PaginaEmpresa.Add('Puerto HTTPS:', False);
  PaginaEmpresa.Values[0] := ExpandConstant('{param:EMPRESA|Mi empresa}');
  PaginaEmpresa.Values[1] := ExpandConstant('{param:PUERTO|8443}');

  PaginaLicencia := CreateInputFilePage(PaginaEmpresa.ID,
    'Licencia', 'Archivo de licencia de GAMA Suite (opcional).',
    'Si tiene el archivo de licencia, selecciónelo. Sin licencia solo se activa el núcleo; podrá cargarla después.');
  PaginaLicencia.Add('Archivo de licencia:', 'Licencia de GAMA (*.json)|*.json|Todos los archivos|*.*', '.json');
  PaginaLicencia.Values[0] := ExpandConstant('{param:LICENCIA|}');
end;

function ShouldSkipPage(PageID: Integer): Boolean;
begin
  Result := EsActualizacion and (PageID = PaginaEmpresa.ID);
end;

function NextButtonClick(CurPageID: Integer): Boolean;
var
  Puerto: Integer;
begin
  Result := True;
  if CurPageID = PaginaEmpresa.ID then
  begin
    PaginaEmpresa.Values[0] := Trim(PaginaEmpresa.Values[0]);
    StringChangeEx(PaginaEmpresa.Values[0], '"', '', True);
    Puerto := StrToIntDef(Trim(PaginaEmpresa.Values[1]), 0);
    if (Length(PaginaEmpresa.Values[0]) < 2) or (Length(PaginaEmpresa.Values[0]) > 120) then
    begin
      SuppressibleMsgBox('Escriba el nombre de la empresa (entre 2 y 120 caracteres).', mbError, MB_OK, IDOK);
      Result := False;
    end
    else if (Puerto < 1024) or (Puerto > 65535) then
    begin
      SuppressibleMsgBox('El puerto debe ser un número entre 1024 y 65535.', mbError, MB_OK, IDOK);
      Result := False;
    end;
  end;
  if (CurPageID = PaginaLicencia.ID) and (PaginaLicencia.Values[0] <> '') and not FileExists(PaginaLicencia.Values[0]) then
  begin
    SuppressibleMsgBox('No se encuentra el archivo de licencia seleccionado.', mbError, MB_OK, IDOK);
    Result := False;
  end;
end;

{ En una actualización los servicios deben estar detenidos para reemplazar sus archivos. }
function PrepareToInstall(var NeedsRestart: Boolean): String;
var
  Codigo: Integer;
begin
  Exec(ExpandConstant('{sys}\net.exe'), 'stop GAMA-Servidor', '', SW_HIDE, ewWaitUntilTerminated, Codigo);
  Exec(ExpandConstant('{sys}\net.exe'), 'stop GAMA-PostgreSQL /y', '', SW_HIDE, ewWaitUntilTerminated, Codigo);
  Result := '';
end;

procedure CurStepChanged(CurStep: TSetupStep);
var
  Parametros, Empresa: String;
  Codigo: Integer;
begin
  if CurStep <> ssPostInstall then
    Exit;
  Empresa := PaginaEmpresa.Values[0];
  StringChangeEx(Empresa, '"', '', True);
  Parametros := '-NoProfile -ExecutionPolicy Bypass -File "' + ExpandConstant('{app}\scripts\instalar.ps1') + '"' +
    ' -App "' + ExpandConstant('{app}') + '"' +
    ' -Datos "' + CarpetaDatos + '"' +
    ' -Empresa "' + Empresa + '"' +
    ' -Puerto ' + PuertoActual('');
  if PaginaLicencia.Values[0] <> '' then
    Parametros := Parametros + ' -Licencia "' + PaginaLicencia.Values[0] + '"';
  if '{#ClavePublica}' <> '' then
    Parametros := Parametros + ' -ClavePublica "{#ClavePublica}"';
  WizardForm.StatusLabel.Caption := 'Creando la base de datos, el certificado y los servicios...';
  if not Exec(ExpandConstant('{sys}\WindowsPowerShell\v1.0\powershell.exe'), Parametros, '', SW_HIDE,
              ewWaitUntilTerminated, Codigo) or (Codigo <> 0) then
  begin
    ConfiguracionFallida := True;
    Log('La configuración terminó con código ' + IntToStr(Codigo));
    SuppressibleMsgBox('La configuración de GAMA Suite no terminó correctamente.' + #13#10 +
      'Revise el registro en ' + CarpetaDatos + '\logs\instalacion.log', mbCriticalError, MB_OK, IDOK);
  end;
end;

procedure CurPageChanged(CurPageID: Integer);
begin
  if (CurPageID = wpFinished) and not ConfiguracionFallida then
  begin
    if EraActualizacion then
      WizardForm.FinishedLabel.Caption := 'GAMA Suite se actualizó y está en marcha.' + #13#10#13#10 +
        'Consola: https://localhost:' + PuertoActual('') + '/consola/'
    else
      WizardForm.FinishedLabel.Caption := 'GAMA Suite está instalada y en marcha.' + #13#10#13#10 +
        'La contraseña temporal del administrador está en:' + #13#10 + CarpetaDatos + '\acceso-inicial.txt' + #13#10 +
        '(solo administradores; GAMA pedirá cambiarla al entrar).' + #13#10#13#10 +
        'El navegador avisará que el certificado es propio de esta planta: es normal. ' +
        'Compruebe que la huella coincida con la del archivo de acceso.';
  end;
end;
