/* @file CalibCBlock.java
 *
 * @author marco corvi
 * @date nov 2011
 *
 * @brief TopoDroid DistoX calibration data
 * --------------------------------------------------------
 *  Copyright This sowftare is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 */
package org.hwyl.sexytopo.control.calibration.fromtopodroid;

import java.io.StringWriter;
import java.io.PrintWriter;
import java.util.Locale;

public class CalibCBlock
{
  private static final float grad2rad = TDMath.GRAD2RAD;


  public long mId;
  public long mCalibId;
  public long gx;
  public long gy;
  public long gz;
  public long mx;
  public long my;
  public long mz;
  public long  mGroup;
  public float mBearing;  // computed compass
  public float mClino;    // computed clino
  public float mRoll;     // computed roll
  public float mError;    // error in the calibration algo associated to this data
  public long mStatus;

  boolean isSaturated()
  { 
    return ( mx >= 32768 || my >= 32768 || mz >= 32768 );
  }

  boolean isGZero()
  {
    return ( gx == 0 && gy == 0 && gz == 0 );
  }

  public CalibCBlock()
  {
    mId = 0;
    mCalibId = 0;
    gx = 0;
    gy = 0;
    gz = 0;
    mx = 0;
    my = 0;
    mz = 0;
    mGroup = 0;
    mError = 0.0f;
  }

  public boolean isFarFrom( float b0, float c0, float thr )
  {
    computeBearingAndClino();
    float c = c0 * grad2rad;
    float b = b0 * grad2rad;
    Vector v1 = new Vector( (float)Math.cos(c) * (float)Math.cos(b), 
                            (float)Math.cos(c) * (float)Math.sin(b),
                            (float)Math.sin(c) );
    c = mClino   * grad2rad; 
    b = mBearing * grad2rad;
    Vector v2 = new Vector( (float)Math.cos(c) * (float)Math.cos(b), 
                            (float)Math.cos(c) * (float)Math.sin(b),
                            (float)Math.sin(c) );
    float x = v1.dot(v2);
    return x < thr; // 0.70: approx 45 degrees
  }

  public void setId( long id, long cid ) 
  {
    mId = id;
    mCalibId = cid;
  }
  // FIXME ZERO-DATA
  public void setGroupIfNonZero( long g ) { mGroup = isGZero() ? 0 : g; }

  public void setGroup( long g ) { mGroup = g; }
  public void setError( float err ) { mError = err; }


  void setStatus( long s ) { mStatus = s; }

  public void setData( long gx0, long gy0, long gz0, long mx0, long my0, long mz0 )
  {
    gx = ( gx0 > TopoDroidUtil.ZERO ) ? gx0 - TopoDroidUtil.NEG : gx0;
    gy = ( gy0 > TopoDroidUtil.ZERO ) ? gy0 - TopoDroidUtil.NEG : gy0;
    gz = ( gz0 > TopoDroidUtil.ZERO ) ? gz0 - TopoDroidUtil.NEG : gz0;
    mx = ( mx0 > TopoDroidUtil.ZERO ) ? mx0 - TopoDroidUtil.NEG : mx0;
    my = ( my0 > TopoDroidUtil.ZERO ) ? my0 - TopoDroidUtil.NEG : my0;
    mz = ( mz0 > TopoDroidUtil.ZERO ) ? mz0 - TopoDroidUtil.NEG : mz0;
  } 

  public void computeBearingAndClino()
  {
    float f = TopoDroidUtil.FV;
    // StringWriter sw = new StringWriter();
    // PrintWriter pw = new PrintWriter( sw );
    // pw.format("Locale.US, G %d %d %d M %d %d %d E %.2f", gx, gy, gz, mx, my, mz, mError );
    // TDLog.Log( TDLog.LOG_DATA, sw.getBuffer().toString() );
    Vector g = new Vector( gx/f, gy/f, gz/f );
    Vector m = new Vector( mx/f, my/f, mz/f );
    doComputeBearingAndClino( g, m );
  }

  public void computeBearingAndClino( CalibAlgo calib )
  {
    float f = TopoDroidUtil.FV;
    Vector g = new Vector( gx/f, gy/f, gz/f );
    Vector m = new Vector( mx/f, my/f, mz/f );
    Vector g0 = calib.GetAG().timesV( g );
    Vector m0 = calib.GetAM().timesV( m );
    Vector g1 = calib.GetBG().plus( g0 );
    Vector m1 = calib.GetBM().plus( m0 );
    doComputeBearingAndClino( g1, m1 );
  }

  private void doComputeBearingAndClino( Vector g, Vector m )
  {
    g.normalize();
    m.normalize();
    Vector e = new Vector( 1.0f, 0.0f, 0.0f );
    Vector y = m.cross( g );
    Vector x = g.cross( y );
    y.normalize();
    x.normalize();
    float ex = e.dot( x );
    float ey = e.dot( y );
    float ez = e.dot( g );
    mBearing =   TDMath.atan2( -ey, ex );
    mClino   = - TDMath.atan2( ez, (float)Math.sqrt(ex*ex+ey*ey) );
    mRoll    =   TDMath.atan2( g.y, g.z );
    if ( mBearing < 0.0f ) mBearing += TDMath.M_2PI;
    if ( mRoll < 0.0f ) mRoll += TDMath.M_2PI;
    mClino   *= TDMath.RAD2GRAD;
    mBearing *= TDMath.RAD2GRAD;
    mRoll    *= TDMath.RAD2GRAD;
  }

  public String toString()
  {
    float ua = TDSetting.mUnitAngle;

    StringWriter sw = new StringWriter();
    PrintWriter pw  = new PrintWriter(sw);
    computeBearingAndClino();
    pw.format(Locale.US, "%d <%d> %5.1f %5.1f %5.1f %6.4f",
      mId, mGroup, mBearing*ua, mClino*ua, mRoll*ua, mError*TDMath.RAD2GRAD );
    if ( TDSetting.mRawCData == 1 ) {
      pw.format( "  %d %d %d  %d %d %d", gx, gy, gz, mx, my, mz );
    } else if ( TDSetting.mRawCData == 2 ) {
      pw.format( "  %04x %04x %04x  %04x %04x %04x", gx & 0xffff, gy & 0xffff, gz & 0xffff, mx & 0xffff, my & 0xffff, mz & 0xffff );
    }
    return sw.getBuffer().toString();
  }
}

