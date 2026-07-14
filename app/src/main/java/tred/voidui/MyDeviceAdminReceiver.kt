package tred.voidui

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class MyDeviceAdminReceiver : DeviceAdminReceiver() {
    
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Toast.makeText(context, "Device Admin Enabled", Toast.LENGTH_SHORT).show()
    }
    
    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Toast.makeText(context, "Device Admin Disabled", Toast.LENGTH_SHORT).show()
    }
    
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "This will disable the Minimalist Launcher admin features"
    }
    
    override fun onPasswordChanged(context: Context, intent: Intent) {
        super.onPasswordChanged(context, intent)
        Toast.makeText(context, "Password Changed", Toast.LENGTH_SHORT).show()
    }
    
    override fun onPasswordFailed(context: Context, intent: Intent) {
        super.onPasswordFailed(context, intent)
        Toast.makeText(context, "Password Failed", Toast.LENGTH_SHORT).show()
    }
    
    override fun onPasswordSucceeded(context: Context, intent: Intent) {
        super.onPasswordSucceeded(context, intent)
        Toast.makeText(context, "Password Succeeded", Toast.LENGTH_SHORT).show()
    }
}
