#requires -Version 5.1
function Install-ReleaseApk {
    param(
        [Parameter(Mandatory = $true)][string]$AdbPath,
        [Parameter(Mandatory = $true)][string]$Apk,
        [Parameter(Mandatory = $true)][string]$LogDirectory,
        [string]$DeviceSerial,
        [switch]$NonInteractive
    )
    $listing = @(& $AdbPath devices -l 2>&1)
    $listExit = $LASTEXITCODE
    $listing | Set-Content -LiteralPath "$LogDirectory/adb-devices.log"
    if ($listExit -ne 0) { throw 'APK built, but ADB device detection failed. See adb-devices.log.' }
    $devices = @($listing | ForEach-Object {
        if ("$_" -match '^(\S+)\s+device(?:\s|$)') { $Matches[1] }
    })
    if ($DeviceSerial) {
        if ($DeviceSerial -notin $devices) {
            throw "APK built, but device '$DeviceSerial' is unavailable. Check USB debugging and authorization."
        }
    } elseif ($devices.Count -eq 0) {
        Write-Warning 'APK built. No authorized ADB device: installation skipped. Connect/unlock the phone and authorize USB debugging.'
        return
    } elseif ($devices.Count -eq 1) {
        $DeviceSerial = $devices[0]
    } else {
        if ($NonInteractive) { throw 'APK built. Multiple ADB devices: specify -DeviceSerial.' }
        for ($i = 0; $i -lt $devices.Count; $i++) { Write-Host "[$($i + 1)] $($devices[$i])" }
        $choice = Read-Host 'Select the device number to install'
        $number = 0
        if (-not [int]::TryParse($choice, [ref]$number) -or $number -lt 1 -or $number -gt $devices.Count) {
            throw 'Invalid device selection. APK retained; no installation performed.'
        }
        $DeviceSerial = $devices[$number - 1]
    }
    Write-Host "Installing on $DeviceSerial (preserving app data)..."
    $result = @(& $AdbPath -s $DeviceSerial install -r $Apk 2>&1)
    $installExit = $LASTEXITCODE
    $result | Set-Content -LiteralPath "$LogDirectory/adb-install.log"
    $result | ForEach-Object { Write-Host "$_" }
    if ($installExit -ne 0 -or -not ($result | Where-Object { "$_".Trim() -eq 'Success' })) {
        if (($result -join "`n") -match 'INSTALL_FAILED_UPDATE_INCOMPATIBLE|signatures do not match') {
            throw 'Signature differs from the installed app. APK retained; no app was uninstalled. Back up data before deciding whether to remove the old app.'
        }
        throw 'APK built, but ADB installation failed. See adb-install.log.'
    }
    Write-Host "Installation successful: $DeviceSerial"
}
