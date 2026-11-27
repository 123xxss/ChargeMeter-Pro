/*
 * This file is auto-generated.  DO NOT MODIFY.
 */
package com.dsh.powermeter.shizuku;
public interface IBatteryUserService extends android.os.IInterface
{
  /** Default implementation for IBatteryUserService. */
  public static class Default implements com.dsh.powermeter.shizuku.IBatteryUserService
  {
    /** Shizuku 保留的销毁事务号，用于停止 UserService。 */
    @Override public void destroy() throws android.os.RemoteException
    {
    }
    /** 返回 "voltage_now;current_now;temp;charge_counter;status;voltage_max"（只读白名单节点）。 */
    @Override public java.lang.String readBattery() throws android.os.RemoteException
    {
      return null;
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements com.dsh.powermeter.shizuku.IBatteryUserService
  {
    /** Construct the stub at attach it to the interface. */
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an com.dsh.powermeter.shizuku.IBatteryUserService interface,
     * generating a proxy if needed.
     */
    public static com.dsh.powermeter.shizuku.IBatteryUserService asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof com.dsh.powermeter.shizuku.IBatteryUserService))) {
        return ((com.dsh.powermeter.shizuku.IBatteryUserService)iin);
      }
      return new com.dsh.powermeter.shizuku.IBatteryUserService.Stub.Proxy(obj);
    }
    @Override public android.os.IBinder asBinder()
    {
      return this;
    }
    @Override public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException
    {
      java.lang.String descriptor = DESCRIPTOR;
      if (code >= android.os.IBinder.FIRST_CALL_TRANSACTION && code <= android.os.IBinder.LAST_CALL_TRANSACTION) {
        data.enforceInterface(descriptor);
      }
      switch (code)
      {
        case INTERFACE_TRANSACTION:
        {
          reply.writeString(descriptor);
          return true;
        }
      }
      switch (code)
      {
        case TRANSACTION_destroy:
        {
          this.destroy();
          reply.writeNoException();
          break;
        }
        case TRANSACTION_readBattery:
        {
          java.lang.String _result = this.readBattery();
          reply.writeNoException();
          reply.writeString(_result);
          break;
        }
        default:
        {
          return super.onTransact(code, data, reply, flags);
        }
      }
      return true;
    }
    private static class Proxy implements com.dsh.powermeter.shizuku.IBatteryUserService
    {
      private android.os.IBinder mRemote;
      Proxy(android.os.IBinder remote)
      {
        mRemote = remote;
      }
      @Override public android.os.IBinder asBinder()
      {
        return mRemote;
      }
      public java.lang.String getInterfaceDescriptor()
      {
        return DESCRIPTOR;
      }
      /** Shizuku 保留的销毁事务号，用于停止 UserService。 */
      @Override public void destroy() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_destroy, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      /** 返回 "voltage_now;current_now;temp;charge_counter;status;voltage_max"（只读白名单节点）。 */
      @Override public java.lang.String readBattery() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        java.lang.String _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_readBattery, _data, _reply, 0);
          _reply.readException();
          _result = _reply.readString();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
    }
    static final int TRANSACTION_destroy = (android.os.IBinder.FIRST_CALL_TRANSACTION + 16777114);
    static final int TRANSACTION_readBattery = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
  }
  public static final java.lang.String DESCRIPTOR = "com.dsh.powermeter.shizuku.IBatteryUserService";
  /** Shizuku 保留的销毁事务号，用于停止 UserService。 */
  public void destroy() throws android.os.RemoteException;
  /** 返回 "voltage_now;current_now;temp;charge_counter;status;voltage_max"（只读白名单节点）。 */
  public java.lang.String readBattery() throws android.os.RemoteException;
}
