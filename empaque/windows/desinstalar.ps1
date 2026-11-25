#Requires -RunAsAdministrator
<#
  Quita los servicios y la regla de firewall de GAMA Suite. Los DATOS se conservan en la carpeta
  de datos (base de datos, configuración, certificado y respaldos) para poder reinstalar o restaurar.
#>
param([Parameter(Mandatory)] [string] $App)

$ErrorActionPreference = 'Continue'
$winsw = Join-Path $App 'servicio\gama-servicio.exe'
if (Get-Service -Name 'GAMA-Servidor' -ErrorAction SilentlyContinue) {
    Stop-Service -Name 'GAMA-Servidor' -Force -ErrorAction SilentlyContinue
    if (Test-Path $winsw) { & $winsw uninstall } else { & sc.exe delete 'GAMA-Servidor' }
}
if (Get-Service -Name 'GAMA-PostgreSQL' -ErrorAction SilentlyContinue) {
    Stop-Service -Name 'GAMA-PostgreSQL' -Force -ErrorAction SilentlyContinue
    $pgctl = Join-Path $App 'pgsql\bin\pg_ctl.exe'
    if (Test-Path $pgctl) { & $pgctl unregister -N 'GAMA-PostgreSQL' } else { & sc.exe delete 'GAMA-PostgreSQL' }
}
Get-NetFirewallRule -DisplayName 'GAMA Suite (HTTPS)' -ErrorAction SilentlyContinue | Remove-NetFirewallRule
# La tarea de respaldo se quita; los respaldos ya hechos se conservan en la carpeta de datos.
Unregister-ScheduledTask -TaskName 'GAMA Suite - Respaldo diario' -Confirm:$false -ErrorAction SilentlyContinue
exit 0
