//File ..\INIT\TYP\XCT1000.TYP
//15.08.97 P.W
//XCT Ê->µScope;
 
[DeviceType XCT1000.TYP]
DeviceName=XCT-ÊScope
CpsAlarm               =   200000
CpsMinimum             =    10000
LnChkMinAtt            =       50.0
LnDeltaPercDet         =       10.0
LnDeltaPercScan        =       10.0
DeltaOffsetLimitQa     =        0.2
DeltaOffsetLimit       =        2.8
DeltaLimitAirLeftRight =        5.0
DeltaPercTime          =       20.0
XSlope                 =      440
XInter                 =     -165
 
 
VoxelSize_A            =        0.6885
VoxelSize_B            =        0.5902
 
[HvCheck]
//Check TubePotential and TubeCurrent (Hv is off)
HvOffChkFlg            = True
//Limits some seconds post "off":
HvOffChkSec_A          =  3.0
HvOffMaxKV_A           = 15.0
HvOffMaxMA_A           =  0.05
//Limits some more seconds post "off":
HvOffChkSec_B          =  8.0
HvOffMaxKV_B           =  5.0
HvOffMaxMA_B           =  0.05
 
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
HvOnMaxDeltaMA_B       =  0.03
 
[Measurement]
// values must be evaluated !!
// QualityVar   =      TH-warning,TH-invalid,TH-abort
MQCheckON       =      TRUE
MQLnChk         =      -1,-40,-60
MQOffsetChk     =       0,-40,-60
MQRangeChk      =       0,-40,-60
EdgeThr         =       0.8
WarnDeltaZmm    =     999.0
// SlicePauseLimit ... [sec]
SlicePauseLimit   =  1800
SlicePauseMinimum =    60
SlicePauseDetOn   =    30
SlicePausePerc    =    20
 
[BackProjection]
CtMirrorHoriz   =       True
CtMirrorVert    =       False
 
[ScoutView]
SvMirrorHoriz   =       True
SvMirrorVert    =       False
SvColorFactor   =     1.0
 
//END
