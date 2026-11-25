#Requires -RunAsAdministrator
<#
  Instalación de GAMA Suite en Windows. La ejecuta el instalador (.exe) con permisos de administrador.
  Idempotente: en una actualización conserva la base, la configuración y el certificado.

  1. PostgreSQL embebido: initdb, servicio «GAMA-PostgreSQL» (NetworkService), solo en localhost.
  2. Base «gama» con su usuario y contraseña aleatoria; archivo de configuración gama.env.
  3. gama-servidor instalar: migraciones, empresa, roles, catálogo, administrador y certificado TLS.
  4. Servicio «GAMA-Servidor» (LocalService), regla de firewall (solo redes privadas y de dominio).
  5. Tarea programada «GAMA Suite - Respaldo diario» (02:15, cuenta SYSTEM) con 14 respaldos de la base.
#>
param(
    [Parameter(Mandatory)] [string] $App,
    [Parameter(Mandatory)] [string] $Datos,
    [string] $Empresa = 'Mi empresa',
    [int] $Puerto = 8443,
    [int] $PuertoBD = 5544,
    [string] $Licencia = '',
    [string] $ClavePublica = ''
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$Logs = Join-Path $Datos 'logs'
New-Item -ItemType Directory -Force -Path $Datos, $Logs, (Join-Path $Datos 'tls') | Out-Null
Start-Transcript -Path (Join-Path $Logs 'instalacion.log') -Append | Out-Null

$PgBin = Join-Path $App 'pgsql\bin'
$PgData = Join-Path $Datos 'pgdata'
$Conf = Join-Path $Datos 'gama.env'
$Exe = Join-Path $App 'servidor\gama-servidor.exe'
$Acceso = Join-Path $Datos 'acceso-inicial.txt'
$Sin_BOM = New-Object System.Text.UTF8Encoding $false

function Aleatorio([int] $Largo) {
    # Generador criptográfico; rechazo de muestras para no sesgar el alfabeto.
    $alfabeto = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789'
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $sb = New-Object System.Text.StringBuilder
    $b = New-Object byte[] 1
    while ($sb.Length -lt $Largo) {
        $rng.GetBytes($b)
        if ($b[0] -lt (256 - (256 % $alfabeto.Length))) { [void]$sb.Append($alfabeto[$b[0] % $alfabeto.Length]) }
    }
    $rng.Dispose()
    return $sb.ToString()
}

function Ejecutar([string] $Programa, [string[]] $Argumentos) {
    & $Programa @Argumentos
    # Sin los argumentos en el mensaje: algunos llevan contraseñas y el registro de instalación no debe tenerlas.
    if ($LASTEXITCODE -ne 0) { throw "Falló $(Split-Path -Leaf $Programa) (código $LASTEXITCODE)" }
}

function Permisos([string] $Ruta, [string] $Cuenta, [string] $Derechos) {
    Ejecutar 'icacls.exe' @($Ruta, '/grant', "*${Cuenta}:${Derechos}", '/Q')
}

# SID bien conocidos: válidos en cualquier idioma de Windows.
$SID_ADMINS = 'S-1-5-32-544'; $SID_SYSTEM = 'S-1-5-18'; $SID_LOCALSERVICE = 'S-1-5-19'; $SID_NETWORKSERVICE = 'S-1-5-20'

try {
    # --- Carpeta de datos: solo administradores y SYSTEM, más permisos puntuales a los servicios ---------
    Ejecutar 'icacls.exe' @($Datos, '/inheritance:r', '/grant:r', "*${SID_ADMINS}:(OI)(CI)F", "*${SID_SYSTEM}:(OI)(CI)F", '/Q')
    Permisos $Logs $SID_LOCALSERVICE '(OI)(CI)M'
    Permisos (Join-Path $Datos 'tls') $SID_LOCALSERVICE '(OI)(CI)RX'
    Permisos $App $SID_LOCALSERVICE '(OI)(CI)RX'
    Permisos (Join-Path $App 'pgsql') $SID_NETWORKSERVICE '(OI)(CI)RX'

    # --- PostgreSQL ---------------------------------------------------------------------------------
    $servicioBD = Get-Service -Name 'GAMA-PostgreSQL' -ErrorAction SilentlyContinue
    if (-not (Test-Path (Join-Path $PgData 'PG_VERSION'))) {
        if (Test-Path $Conf) { throw "Existe $Conf pero no la base en $PgData. Restaure el respaldo o borre la configuración." }
        Write-Host 'Creando la base de datos...'
        # initdb corre con un token restringido (sin el grupo Administradores): necesita permiso explícito
        # del usuario que instala sobre la carpeta de la base.
        New-Item -ItemType Directory -Force -Path $PgData | Out-Null
        $sidUsuario = [System.Security.Principal.WindowsIdentity]::GetCurrent().User.Value
        Permisos $PgData $sidUsuario '(OI)(CI)F'
        $superclave = Aleatorio 32
        $archivoClave = Join-Path $env:TEMP ("gama-" + [guid]::NewGuid().ToString() + '.txt')
        [IO.File]::WriteAllText($archivoClave, $superclave, $Sin_BOM)
        try {
            Ejecutar (Join-Path $PgBin 'initdb.exe') @('-D', $PgData, '-U', 'postgres', "--pwfile=$archivoClave", '-E', 'UTF8', '--no-locale', '-A', 'scram-sha-256')
        } finally {
            Remove-Item -Force $archivoClave -ErrorAction SilentlyContinue
        }
        Add-Content -Path (Join-Path $PgData 'postgresql.conf') -Encoding ASCII -Value @(
            '', '# GAMA Suite', "port = $PuertoBD", "listen_addresses = 'localhost'"
        )
        # Clave del superusuario para el administrador de base de datos (carpeta solo de administradores).
        [IO.File]::WriteAllText((Join-Path $Datos 'postgres-superusuario.txt'), "Usuario: postgres`r`nPuerto: $PuertoBD`r`nClave: $superclave`r`n", $Sin_BOM)
        Permisos $PgData $SID_NETWORKSERVICE '(OI)(CI)M'
        if (-not $servicioBD) {
            Ejecutar (Join-Path $PgBin 'pg_ctl.exe') @('register', '-N', 'GAMA-PostgreSQL', '-U', 'NT AUTHORITY\NetworkService', '-D', $PgData, '-S', 'auto', '-w')
        }
        Start-Service -Name 'GAMA-PostgreSQL'
        Ejecutar (Join-Path $PgBin 'pg_isready.exe') @('-h', '127.0.0.1', '-p', "$PuertoBD", '-t', '60')

        $claveBD = Aleatorio 32
        $env:PGPASSWORD = $superclave
        try {
            $psql = Join-Path $PgBin 'psql.exe'
            Ejecutar $psql @('-X', '-v', 'ON_ERROR_STOP=1', '-h', '127.0.0.1', '-p', "$PuertoBD", '-U', 'postgres', '-d', 'postgres', '-c', "create role gama with login password '$claveBD'")
            Ejecutar $psql @('-X', '-v', 'ON_ERROR_STOP=1', '-h', '127.0.0.1', '-p', "$PuertoBD", '-U', 'postgres', '-d', 'postgres', '-c', "create database gama owner gama encoding 'UTF8' template template0")
        } finally {
            Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
        }

        # --- Configuración ------------------------------------------------------------------------------
        $ip = (Get-NetIPConfiguration | Where-Object { $_.IPv4DefaultGateway -and $_.NetAdapter.Status -eq 'Up' } |
               Select-Object -First 1).IPv4Address.IPAddress
        if (-not $ip) { $ip = $env:COMPUTERNAME }
        $lineas = @(
            '# Configuración de GAMA Suite. Generada al instalar; guarde una copia segura.',
            "GAMA_BASE_DATOS_URL=postgresql+psycopg://gama:$claveBD@127.0.0.1:$PuertoBD/gama",
            "GAMA_JWT_SECRETO=$(Aleatorio 64)",
            "GAMA_PUERTO=$Puerto",
            "GAMA_TLS_CERTIFICADO=$(Join-Path $Datos 'tls\servidor.crt')",
            "GAMA_TLS_CLAVE=$(Join-Path $Datos 'tls\servidor.key')",
            "GAMA_URL_PUBLICA=https://${ip}:$Puerto",
            "GAMA_APK_DIR=$(Join-Path $App 'apk')",
            'GAMA_ZONA_HORARIA=America/Caracas'
        )
        [IO.File]::WriteAllText($Conf, ($lineas -join "`r`n") + "`r`n", $Sin_BOM)
    } else {
        Write-Host 'Base de datos existente: se conserva.'
        if (-not $servicioBD) {
            Ejecutar (Join-Path $PgBin 'pg_ctl.exe') @('register', '-N', 'GAMA-PostgreSQL', '-U', 'NT AUTHORITY\NetworkService', '-D', $PgData, '-S', 'auto', '-w')
        }
        Start-Service -Name 'GAMA-PostgreSQL'
        Ejecutar (Join-Path $PgBin 'pg_isready.exe') @('-h', '127.0.0.1', '-p', "$PuertoBD", '-t', '60')
    }
    Permisos $Conf $SID_LOCALSERVICE 'R'

    # --- Licencia (opcional) ------------------------------------------------------------------------
    if ($Licencia -and (Test-Path $Licencia)) {
        $destino = Join-Path $Datos 'licencia.json'
        Copy-Item -Force $Licencia $destino
        Permisos $destino $SID_LOCALSERVICE 'R'
        $texto = [IO.File]::ReadAllText($Conf) -split "`r?`n" | Where-Object { $_ -and $_ -notmatch '^GAMA_LICENCIA_(ARCHIVO|CLAVE_PUBLICA)=' }
        $texto += "GAMA_LICENCIA_ARCHIVO=$destino"
        if ($ClavePublica) { $texto += "GAMA_LICENCIA_CLAVE_PUBLICA=$ClavePublica" }
        [IO.File]::WriteAllText($Conf, ($texto -join "`r`n") + "`r`n", $Sin_BOM)
    }

    # --- Carpeta de respaldos en la configuración (también en actualizaciones de versiones anteriores) ---
    $Respaldos = Join-Path $Datos 'respaldos'
    New-Item -ItemType Directory -Force -Path $Respaldos | Out-Null
    $lineas = [IO.File]::ReadAllText($Conf) -split "`r?`n" | Where-Object { $_ }
    if (-not ($lineas | Where-Object { $_ -match '^GAMA_RESPALDO_DIR=' })) {
        $lineas += "GAMA_RESPALDO_DIR=$Respaldos"
        [IO.File]::WriteAllText($Conf, ($lineas -join "`r`n") + "`r`n", $Sin_BOM)
    }

    # --- Migraciones, empresa, roles, administrador y certificado --------------------------------------
    $env:GAMA_CONFIG = $Conf
    Ejecutar $Exe @('instalar', '--empresa', $Empresa, '--credenciales', $Acceso)

    # --- Servicio del servidor (WinSW) --------------------------------------------------------------
    $carpetaServicio = Join-Path $App 'servicio'
    $xml = @"
<service>
  <id>GAMA-Servidor</id>
  <name>GAMA Suite (servidor)</name>
  <description>API, consola web y MDM de GAMA Suite.</description>
  <executable>$Exe</executable>
  <arguments>servir</arguments>
  <workingdirectory>$Logs</workingdirectory>
  <env name="GAMA_CONFIG" value="$Conf"/>
  <depend>GAMA-PostgreSQL</depend>
  <startmode>Automatic</startmode>
  <onfailure action="restart" delay="10 sec"/>
  <onfailure action="restart" delay="30 sec"/>
  <resetfailure>1 hour</resetfailure>
  <logpath>$Logs</logpath>
  <log mode="roll-by-size"><sizeThreshold>10240</sizeThreshold><keepFiles>8</keepFiles></log>
  <stoptimeout>20 sec</stoptimeout>
</service>
"@
    [IO.File]::WriteAllText((Join-Path $carpetaServicio 'gama-servicio.xml'), $xml, $Sin_BOM)
    $winsw = Join-Path $carpetaServicio 'gama-servicio.exe'
    if (Get-Service -Name 'GAMA-Servidor' -ErrorAction SilentlyContinue) {
        Stop-Service -Name 'GAMA-Servidor' -Force -ErrorAction SilentlyContinue
        Ejecutar $winsw @('uninstall')
        Start-Sleep -Seconds 2
    }
    Ejecutar $winsw @('install')
    # Cuenta con privilegios mínimos (sin contraseña: cuenta integrada).
    $svc = Get-CimInstance -ClassName Win32_Service -Filter "Name='GAMA-Servidor'"
    $r = Invoke-CimMethod -InputObject $svc -MethodName Change -Arguments @{ StartName = 'NT AUTHORITY\LocalService'; StartPassword = '' }
    if ($r.ReturnValue -ne 0) { throw "No se pudo asignar LocalService al servicio (código $($r.ReturnValue))" }
    Start-Service -Name 'GAMA-Servidor'

    # --- Respaldo diario de la base (pg_dump de la copia de PostgreSQL instalada) ------------------------
    $cmdRespaldo = Join-Path $carpetaServicio 'respaldar.cmd'
    $lineasCmd = @(
        '@echo off',
        "set GAMA_CONFIG=$Conf",
        "`"$Exe`" respaldar >> `"$(Join-Path $Logs 'respaldo.log')`" 2>&1"
    )
    [IO.File]::WriteAllText($cmdRespaldo, ($lineasCmd -join "`r`n") + "`r`n", $Sin_BOM)
    $accion = New-ScheduledTaskAction -Execute 'cmd.exe' -Argument "/c `"$cmdRespaldo`""
    $disparo = New-ScheduledTaskTrigger -Daily -At '02:15'
    $cuenta = New-ScheduledTaskPrincipal -UserId 'S-1-5-18' -LogonType ServiceAccount -RunLevel Highest
    $ajustesTarea = New-ScheduledTaskSettingsSet -StartWhenAvailable -ExecutionTimeLimit (New-TimeSpan -Hours 2)
    Register-ScheduledTask -TaskName 'GAMA Suite - Respaldo diario' -Action $accion -Trigger $disparo `
        -Principal $cuenta -Settings $ajustesTarea -Force | Out-Null

    # --- Firewall: solo redes privadas y de dominio ----------------------------------------------------
    Get-NetFirewallRule -DisplayName 'GAMA Suite (HTTPS)' -ErrorAction SilentlyContinue | Remove-NetFirewallRule
    New-NetFirewallRule -DisplayName 'GAMA Suite (HTTPS)' -Direction Inbound -Protocol TCP -LocalPort $Puerto `
        -Action Allow -Profile Domain, Private | Out-Null

    # --- Comprobación final -----------------------------------------------------------------------
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    [Net.ServicePointManager]::ServerCertificateValidationCallback = { $true }  # solo esta consulta local
    $ok = $false
    for ($i = 0; $i -lt 60; $i++) {
        try {
            $r = Invoke-WebRequest -UseBasicParsing -TimeoutSec 5 -Uri "https://127.0.0.1:$Puerto/api/v1/salud"
            if ($r.StatusCode -eq 200) { $ok = $true; break }
        } catch { Start-Sleep -Seconds 2 }
    }
    if (-not $ok) { throw "El servidor no respondió en https://127.0.0.1:$Puerto. Revise $Logs" }
    Write-Host "GAMA Suite instalada y en marcha en https://127.0.0.1:$Puerto/consola/"
    Stop-Transcript | Out-Null
    exit 0
} catch {
    Write-Host "ERROR: $($_.Exception.Message)"
    Stop-Transcript | Out-Null
    exit 1
}
