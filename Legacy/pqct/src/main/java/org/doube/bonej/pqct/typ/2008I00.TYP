//File ..\INIT\TYP\2008I00.TYP
 
[DeviceType 2008I00.TYP]
DeviceName             = XCT-2008
CpsAlarm               =   550000
CpsMinimum             =   200000
LnChkMinAtt            =       50.0
LnDeltaPercDet         =        5.0
LnDeltaPercScan        =        5.0
DeltaOffsetLimitQa     =        0.2
DeltaOffsetLimit       =        2.8
DeltaLimitAirLeftRight =        5.0
DeltaPercTime          =       20.0
XSlope                 =     1409
XInter                 =     -306
 
 
VoxelSize_A            =        0.6885
VoxelSize_B            =        0.5902
 
[HvCheck]
//Check TubePotential and TubeCurrent (Hv is off)
HvOffChkFlg            = True
//Limits some seconds post "off":
HvOffChkSec_A          =  3.0
HvOffMaxKV_A           = 30.0
HvOffMaxMA_A           =  0.01
//Limits some more seconds post "off":
HvOffChkSec_B          =  8.0
HvOffMaxKV_B           = 10.0
HvOffMaxMA_B           =  0.01
 
//Check TubePotential and TubeCurrent (Hv is ON)
HvOnChkFlg             = True
//Limits some seconds post "ON":
//Seconds added to WaitSec:
HvOnChkSec_A           = -0.5
HvOnMaxDeltaKV_A       = 10.0
HvOnMaxDeltaMA_A       =  0.1
//Limits some more seconds post "ON":
//Seconds added to WaitSec:
HvOnChkSec_B           =  2.0
HvOnMaxDeltaKV_B       =  2.0
HvOnMaxDeltaMA_B       =  0.01
 
[Measurement]
// values must be evaluated !!
// QualityVar   =      TH-warning,TH-invalid,TH-abort
MQCheckON       =      TRUE
MQLnChk         =      -1,-40,-60
MQOffsetChk     =       0,-40,-60
MQRangeChk      =       0,-40,-60
EdgeThr         =       0.8
WarnDeltaZmm    =       2.0
// SlicePauseLimit ... [sec]
SlicePauseLimit   =  1600
SlicePauseMinimum =    60
SlicePauseDetOn   =    30
SlicePausePerc    =    30
 
[BackProjection]
CtMirrorHoriz   =       True
CtMirrorVert    =       False
 
[ScoutView]
SvMirrorHoriz   =       True
SvMirrorVert    =       False
SvColorFactor   =     1.0
 
//END
