package tred.voidui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class HomeAppsAdapter(
    private val apps: List<AppInfo>,
    private val onAppClick: (AppInfo) -> Unit
) : RecyclerView.Adapter<HomeAppsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val appIcon: ImageView = view.findViewById(R.id.appIcon)
        val appName: TextView = view.findViewById(R.id.appName)
        val appPackage: TextView = view.findViewById(R.id.appPackage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_home_app, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = apps[position]
        holder.appIcon.setImageDrawable(app.icon)
        holder.appName.text = app.name
        
        // Show a simplified package name or custom display name
        val displayPackage = when {
            app.packageName.contains("documentsui") -> "documents"
            app.packageName.contains("chrome") -> "browser"
            app.packageName.contains("settings") -> "system"
            app.packageName.contains("camera") -> "camera"
            app.packageName.contains("gallery") -> "photos"
            app.packageName.contains("music") -> "audio"
            app.packageName.contains("video") -> "video"
            app.packageName.contains("phone") -> "dialer"
            app.packageName.contains("contacts") -> "contacts"
            app.packageName.contains("messages") -> "sms"
            app.packageName.contains("calculator") -> "calc"
            app.packageName.contains("calendar") -> "cal"
            app.packageName.contains("clock") -> "time"
            app.packageName.contains("weather") -> "weather"
            app.packageName.contains("maps") -> "nav"
            else -> {
                // Extract the last part of package name and clean it up
                val parts = app.packageName.split(".")
                val lastPart = parts.lastOrNull() ?: app.packageName
                when {
                    lastPart.length > 8 -> lastPart.take(8)
                    else -> lastPart
                }
            }
        }
        
        holder.appPackage.text = displayPackage
        
        holder.itemView.setOnClickListener {
            onAppClick(app)
        }
    }

    override fun getItemCount() = apps.size
}
