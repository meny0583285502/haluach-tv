<# :
@echo off
powershell -NoProfile -ExecutionPolicy Bypass -STA -Command "$s=[IO.File]::ReadAllText('%~f0',[Text.Encoding]::UTF8); iex $s"
exit /b
#>
# ===== ניקוי אפליקציות מטלוויזיית אנדרואיד דרך ADB =====
Add-Type -AssemblyName System.Windows.Forms, System.Drawing
[System.Windows.Forms.Application]::EnableVisualStyles()
$ProgressPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$base = Join-Path $env:LOCALAPPDATA 'TVCleaner'
$adb  = Join-Path $base 'platform-tools\adb.exe'
$script:target = $null

# אפליקציות בידור מוכרות - יסומנו אוטומטית
$known = @{
  'com.netflix.ninja'='Netflix'; 'com.netflix.mediaclient'='Netflix'
  'com.google.android.youtube.tv'='YouTube'; 'com.google.android.youtube'='YouTube'
  'com.google.android.youtube.tvkids'='YouTube Kids'; 'com.google.android.youtube.tvmusic'='YouTube Music'
  'com.google.android.apps.youtube.music'='YouTube Music'
  'com.amazon.amazonvideo.livingroom'='Prime Video'; 'com.disney.disneyplus'='Disney+'
  'com.apple.atve.androidtv.appletv'='Apple TV'; 'com.spotify.tv.android'='Spotify'
  'tv.twitch.android.app'='Twitch'; 'com.hbo.hbonow'='HBO'; 'com.wbd.stream'='Max'
  'com.plexapp.android'='Plex'; 'com.google.android.videos'='Google TV סרטים'
  'com.google.android.play.games'='Play Games'; 'com.zhiliaoapp.musically'='TikTok'
  'com.facebook.katana'='Facebook'; 'com.android.chrome'='Chrome'
  'com.google.android.tvrecommendations'='המלצות תוכן'
}
# אפשרויות שלא מסומנות אוטומטית
$optional = @{
  'com.android.vending'='חנות Play - בלי זה אי אפשר להתקין שום דבר מחדש'
}

# ---------- חלון ----------
$f = New-Object Windows.Forms.Form
$f.Text = 'ניקוי טלוויזיה - הסרת נטפליקס ושאר האפליקציות'
$f.Size = New-Object Drawing.Size(720, 810)
$f.StartPosition = 'CenterScreen'
$f.RightToLeft = 'Yes'; $f.RightToLeftLayout = $true
$f.Font = New-Object Drawing.Font('Segoe UI', 10)

function Add-Ctl($c, $x, $y, $w, $h) { $c.Location = New-Object Drawing.Point($x,$y); $c.Size = New-Object Drawing.Size($w,$h); $f.Controls.Add($c); $c }

$lblHelp = Add-Ctl (New-Object Windows.Forms.Label) 10 10 680 60
$lblHelp.Text = "1. כתוב את כתובת ה-IP של הטלוויזיה ולחץ 'התחבר', ואחר כך 'התקן את הלוח'.`n2. אם מופיעה בטלוויזיה שאלה 'לאפשר ניפוי באגים?' - סמן 'תמיד' ואשר בשלט, ולחץ שוב 'התחבר'."

$lblIp = Add-Ctl (New-Object Windows.Forms.Label) 10 78 160 25; $lblIp.Text = 'כתובת IP של הטלוויזיה:'
$txtIp = Add-Ctl (New-Object Windows.Forms.TextBox) 175 75 180 25; $txtIp.RightToLeft = 'No'
$btnConn = Add-Ctl (New-Object Windows.Forms.Button) 365 73 120 30; $btnConn.Text = 'התחבר'
$lblState = Add-Ctl (New-Object Windows.Forms.Label) 495 78 200 25; $lblState.Text = 'לא מחובר'; $lblState.ForeColor = 'Red'

$btnLoad = Add-Ctl (New-Object Windows.Forms.Button) 10 115 200 32; $btnLoad.Text = 'טען אפליקציות מהטלוויזיה'
$lblList = Add-Ctl (New-Object Windows.Forms.Label) 220 120 270 25; $lblList.Text = 'מה שמסומן - יימחק מהטלוויזיה:'

$btnHal = Add-Ctl (New-Object Windows.Forms.Button) 500 112 190 36; $btnHal.Text = 'התקן את הלוח'; $btnHal.BackColor = 'LightGreen'
$clb = Add-Ctl (New-Object Windows.Forms.CheckedListBox) 10 152 680 250; $clb.CheckOnClick = $true
$btnDel = Add-Ctl (New-Object Windows.Forms.Button) 10 408 200 34; $btnDel.Text = 'מחק את המסומנים'; $btnDel.BackColor = 'MistyRose'

$lblRem = Add-Ctl (New-Object Windows.Forms.Label) 10 452 680 25; $lblRem.Text = 'אפליקציות שכבר נמחקו (אפשר להחזיר אם צריך):'
$lbRem = Add-Ctl (New-Object Windows.Forms.ListBox) 10 478 480 100
$btnRest = Add-Ctl (New-Object Windows.Forms.Button) 500 478 190 32; $btnRest.Text = 'החזר את הנבחרת'
$btnKiosk = Add-Ctl (New-Object Windows.Forms.Button) 220 408 270 34; $btnKiosk.Text = 'מסך תמיד דולק + שקט + בלי עדכונים'; $btnKiosk.BackColor = 'LightBlue'
$btnNotif = Add-Ctl (New-Object Windows.Forms.Button) 500 516 190 52; $btnNotif.Text = 'חסום התראות ומשוב'; $btnNotif.BackColor = 'LightYellow'

$btnExit = Add-Ctl (New-Object Windows.Forms.Button) 10 580 335 40; $btnExit.Text = 'צא מהלוח עכשיו'; $btnExit.BackColor = 'Orange'
$btnLock = Add-Ctl (New-Object Windows.Forms.Button) 355 580 335 40; $btnLock.Text = 'החזר את הלוח (נעילה)'; $btnLock.BackColor = 'LightGreen'
$log = Add-Ctl (New-Object Windows.Forms.TextBox) 10 630 680 125
$log.Multiline = $true; $log.ScrollBars = 'Vertical'; $log.ReadOnly = $true; $log.RightToLeft = 'No'
$log.Font = New-Object Drawing.Font('Consolas', 9)

function Log($s) { $log.AppendText(($s -replace "`r?`n","`r`n") + "`r`n"); [Windows.Forms.Application]::DoEvents() }
function Pkg-Of($item) { ($item -split ' \| ')[-1].Trim() }

function Ensure-Adb {
  if (Test-Path $adb) { return $true }
  Log 'מוריד את כלי ADB מגוגל (פעם ראשונה בלבד)...'
  try {
    New-Item -ItemType Directory -Force -Path $base | Out-Null
    $zip = Join-Path $base 'pt.zip'
    Invoke-WebRequest 'https://dl.google.com/android/repository/platform-tools-latest-windows.zip' -OutFile $zip -UseBasicParsing
    Expand-Archive $zip -DestinationPath $base -Force
    Remove-Item $zip -Force
    Log 'ADB הותקן.'
    return $true
  } catch { Log ('שגיאה בהורדה: ' + $_.Exception.Message); return $false }
}

function Adb([string[]]$a) {
  $full = @(); if ($script:target) { $full += '-s', $script:target }; $full += $a
  Log ('> adb ' + ($full -join ' '))
  $out = (& $adb @full 2>&1 | ForEach-Object { "$_" } | Out-String).Trim()
  if ($out) { Log $out }
  return $out
}

function Require-Conn {
  if (-not $script:target) { [Windows.Forms.MessageBox]::Show('קודם צריך להתחבר לטלוויזיה.') | Out-Null; return $false }
  return $true
}

function Refresh-Removed {
  $all  = (Adb @('shell','pm','list','packages','-u')) -split "`n" | ForEach-Object { ($_ -replace '^package:','').Trim() } | Where-Object { $_ }
  $have = (Adb @('shell','pm','list','packages'))      -split "`n" | ForEach-Object { ($_ -replace '^package:','').Trim() } | Where-Object { $_ }
  $lbRem.Items.Clear()
  foreach ($p in ($all | Where-Object { $have -notcontains $_ } | Sort-Object)) {
    $n = if ($known[$p]) { $known[$p] } elseif ($optional[$p]) { $optional[$p] } else { '' }
    $lbRem.Items.Add(($(if ($n) { "$n | " } else { '' }) + $p)) | Out-Null
  }
}

$btnConn.Add_Click({
  $ip = $txtIp.Text.Trim()
  if (-not $ip) { [Windows.Forms.MessageBox]::Show('כתוב כתובת IP') | Out-Null; return }
  if ($ip -notmatch ':') { $ip = "${ip}:5555" }
  if (-not (Ensure-Adb)) { return }
  $script:target = $null
  Adb @('connect', $ip) | Out-Null
  $script:target = $ip
  $st = Adb @('get-state')
  if ($st -match '^device') {
    $lblState.Text = 'מחובר ✓'; $lblState.ForeColor = 'Green'
  } else {
    $script:target = $null
    $lblState.Text = 'לא מחובר'; $lblState.ForeColor = 'Red'
    [Windows.Forms.MessageBox]::Show("החיבור עוד לא הצליח.`nאם הלוח פתוח בטלוויזיה - צא ממנו קודם (5 פעמים 'חזור' + סיסמה), כי הוא מסתיר את שאלת האישור.`nאם הופיעה שאלה בטלוויזיה - סמן 'תמיד', אשר, ולחץ שוב 'התחבר'.`nאם לא הופיע כלום - בדוק שהפעלת 'ניפוי באגים' בטלוויזיה ושהמחשב והטלוויזיה באותה רשת.") | Out-Null
  }
})

$btnLoad.Add_Click({
  if (-not (Require-Conn)) { return }
  $all   = (Adb @('shell','pm','list','packages'))      -split "`n" | ForEach-Object { ($_ -replace '^package:','').Trim() } | Where-Object { $_ }
  $third = (Adb @('shell','pm','list','packages','-3')) -split "`n" | ForEach-Object { ($_ -replace '^package:','').Trim() } | Where-Object { $_ }
  $clb.Items.Clear()
  foreach ($p in ($all | Sort-Object)) {
    if ($known[$p])        { $clb.Items.Add("$($known[$p]) | $p", $true) | Out-Null }
    elseif ($optional[$p]) { $clb.Items.Add("$($optional[$p]) | $p", $false) | Out-Null }
  }
  foreach ($p in ($third | Sort-Object)) {
    if (-not $known[$p] -and -not $optional[$p]) { $clb.Items.Add("אפליקציה שהותקנה | $p", $false) | Out-Null }
  }
  Refresh-Removed
  Log "נטענו $($clb.Items.Count) אפליקציות."
})

$btnDel.Add_Click({
  if (-not (Require-Conn)) { return }
  $sel = @($clb.CheckedItems | ForEach-Object { Pkg-Of $_ })
  if ($sel.Count -eq 0) { return }
  $ok = [Windows.Forms.MessageBox]::Show("למחוק $($sel.Count) אפליקציות מהטלוויזיה?", 'אישור', 'YesNo')
  if ($ok -ne 'Yes') { return }
  foreach ($p in $sel) { Adb @('shell','am','force-stop',$p) | Out-Null; Adb @('shell','pm','uninstall','-k','--user','0',$p) | Out-Null }
  $btnLoad.PerformClick()
  [Windows.Forms.MessageBox]::Show('בוצע. מומלץ לכבות ולהדליק את הטלוויזיה.') | Out-Null
})

$btnRest.Add_Click({
  if (-not (Require-Conn)) { return }
  if (-not $lbRem.SelectedItem) { return }
  $p = Pkg-Of $lbRem.SelectedItem
  Adb @('shell','cmd','package','install-existing',$p) | Out-Null
  $btnLoad.PerformClick()
})

$btnHal.Add_Click({
  if (-not (Require-Conn)) { return }
  $apk = Join-Path $base 'haluach-tv.apk'
  Log 'מוריד את הלוח (הגרסה האחרונה)...'
  try {
    Invoke-WebRequest 'https://github.com/meny0583285502/haluach-tv/releases/latest/download/haluach-tv.apk' -OutFile $apk -UseBasicParsing
  } catch { Log ('שגיאה בהורדה: ' + $_.Exception.Message); return }
  $r = Adb @('install','-r',$apk)
  if ($r -notmatch 'Success') { [Windows.Forms.MessageBox]::Show('ההתקנה נכשלה - ראה פירוט בחלון למטה') | Out-Null; return }
  # הפעלת הנעילה (נגישות) ישירות - בלי לחפש בהגרות
  $svc = 'com.meny.haluachtv/com.meny.haluachtv.GuardService'
  $cur = (Adb @('shell','settings','get','secure','enabled_accessibility_services')).Trim()
  if ($cur -eq 'null' -or -not $cur) { $new = $svc } elseif ($cur -match [regex]::Escape($svc)) { $new = $cur } else { $new = "$cur`:$svc" }
  Adb @('shell','settings','put','secure','enabled_accessibility_services',$new) | Out-Null
  Adb @('shell','settings','put','secure','accessibility_enabled','1') | Out-Null
  Adb @('shell','am','start','-n','com.meny.haluachtv/.MainActivity') | Out-Null
  [Windows.Forms.MessageBox]::Show("הלוח הותקן ונפתח בטלוויזיה.`nנשאר רק לבחור שם סיסמה עם השלט - וזהו.") | Out-Null
})

$btnNotif.Add_Click({
  if (-not (Require-Conn)) { return }
  # 1. ביטול חלונות קופצים של התראות
  Adb @('shell','settings','put','global','heads_up_notifications_enabled','0') | Out-Null
  # 2. כיבוי בקשות "שלח משוב" והצעות של גוגל
  foreach ($p in @('com.google.android.feedback','com.google.android.tvrecommendations','com.google.android.tv.bugreportsender')) {
    Adb @('shell','pm','disable-user','--user','0',$p) | Out-Null
  }
  # 3. כל אפליקציה ששלחה התראה - נחסמת מלשלוח התראות
  $dump = Adb @('shell','dumpsys','notification')
  $pkgs = [regex]::Matches($dump, 'pkg=([a-zA-Z0-9_.]+)') | ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique
  $keep = @('android','com.android.systemui','com.meny.haluachtv')
  foreach ($p in $pkgs) {
    if ($keep -contains $p) { continue }
    Adb @('shell','cmd','appops','set',$p,'POST_NOTIFICATION','ignore') | Out-Null
    Adb @('shell','pm','revoke',$p,'android.permission.POST_NOTIFICATIONS') | Out-Null
  }
  Log ('נחסמו התראות של: ' + ($pkgs -join ', '))
  [Windows.Forms.MessageBox]::Show("ההתראות נחסמו.`nאם עדיין קופץ משהו - לחץ שוב על הכפתור בזמן שזה מוצג על המסך.") | Out-Null
})

$btnKiosk.Add_Click({
  if (-not (Require-Conn)) { return }
  Log '--- 1. מסך שלא נכבה ---'
  foreach ($c in @(
    @('secure','screensaver_enabled','0'),
    @('secure','screensaver_activate_on_sleep','0'),
    @('secure','screensaver_activate_on_dock','0'),
    @('system','screen_off_timeout','2147483647'),
    @('secure','sleep_timeout','-1'),
    @('secure','attentive_timeout','-1'),
    @('global','stay_on_while_plugged_in','7'))) {
    Adb (@('shell','settings','put') + $c) | Out-Null
  }
  Log '--- 3. השתקה ---'
  Adb @('shell','cmd','media_session','volume','--stream','3','--set','0') | Out-Null
  Log '--- 4. ביטול עדכוני מערכת קופצים ---'
  $all = (Adb @('shell','pm','list','packages')) -split "`n" | ForEach-Object { ($_ -replace '^package:','').Trim() } | Where-Object { $_ }
  $upd = $all | Where-Object { $_ -match '(?i)(\.ota|fota|systemupdate|system\.update|updater|tvupdate|softwareupdate)' -and $_ -notmatch '(?i)(gms|gsf|vending|packageinstaller|haluach)' }
  foreach ($p in $upd) { Adb @('shell','pm','disable-user','--user','0',$p) | Out-Null }
  Log ('כובו: ' + ($(if ($upd) { $upd -join ', ' } else { 'לא נמצא רכיב עדכונים נפרד' })))
  [Windows.Forms.MessageBox]::Show("בוצע:`n- המסך לא ייכבה ולא יעבור לשומר מסך`n- הקול מושתק (כפתורי הווליום בשלט נעולים בגרסה החדשה של הלוח)`n- חלונות עדכון כובו`n`nאחר כך לחץ על 'התקן את הלוח' כדי לקבל את הגרסה החדשה.") | Out-Null
})

function Send-Cmd($cmd) {
  $r = Adb @('shell','am','broadcast','-a','com.meny.haluachtv.CMD','-n','com.meny.haluachtv/.CmdReceiver','--es','cmd',$cmd)
  return ($r -match "ok:$cmd")
}

$btnExit.Add_Click({
  if (-not (Require-Conn)) { return }
  if (Send-Cmd 'exit') {
    Adb @('shell','input','keyevent','KEYCODE_HOME') | Out-Null
    [Windows.Forms.MessageBox]::Show("יצאת מהלוח.`nהוא יחזור לבד כשהטלוויזיה תכבה ותידלק, או כשתלחץ 'החזר את הלוח'.") | Out-Null
  } else {
    [Windows.Forms.MessageBox]::Show("לא הצליח. כנראה מותקנת גרסה ישנה של הלוח - לחץ קודם 'התקן את הלוח' ונסה שוב.") | Out-Null
  }
})

$btnLock.Add_Click({
  if (-not (Require-Conn)) { return }
  if (Send-Cmd 'lock') { Log 'הלוח חזר ונעול.' }
  else { [Windows.Forms.MessageBox]::Show("לא הצליח. לחץ קודם 'התקן את הלוח' ונסה שוב.") | Out-Null }
})

$f.Add_FormClosed({ if (Test-Path $adb) { & $adb disconnect 2>&1 | Out-Null } })
[void]$f.ShowDialog()
