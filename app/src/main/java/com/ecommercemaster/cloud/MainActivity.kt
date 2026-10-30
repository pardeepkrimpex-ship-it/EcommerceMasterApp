package com.ecommercemaster.cloud

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import okhttp3.*
import org.json.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    companion object {
        // Replace these two values with your Supabase project values.
        private const val SUPABASE_URL = "https://fabgfsxivwkrbxohafwc.supabase.co"
        private const val SUPABASE_ANON_KEY = "sb_publishable_v-OPi63YNdFlmURp7XIxmg_8Zc2eEqP"
    }

    private val client = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var accessToken: String? = null
    private var userId: String? = null

    private val platforms = arrayOf("Amazon","Flipkart","Tata 1mg","HealthMug","Meesho","JioMart")
    private val couriers = arrayOf("XpressBees","Delhivery","Amazon","Ekart","DTDC")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showLogin()
    }

    private fun showLogin() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(28,28,28,28) }
        val title = TextView(this).apply { text="Ecommerce Master App"; textSize=25f }
        val email = EditText(this).apply { hint="Email" }
        val pass = EditText(this).apply { hint="Password"; inputType=0x81 }
        val login = Button(this).apply { text="Login"; setOnClickListener { auth(email.text.toString(),pass.text.toString(),false) } }
        val signup = Button(this).apply { text="Create Account"; setOnClickListener { auth(email.text.toString(),pass.text.toString(),true) } }
        box.addView(title); box.addView(email); box.addView(pass); box.addView(login); box.addView(signup)
        setContentView(box)
    }

    private fun auth(email:String,password:String,create:Boolean) {
        if (SUPABASE_URL.contains("YOUR_PROJECT") || SUPABASE_ANON_KEY.contains("YOUR_")) {
            toast("Pehle Supabase URL aur anon key set karein.")
            return
        }
        if (email.isBlank() || password.length < 6) { toast("Valid email aur 6+ character password dein."); return }
        scope.launch {
            try {
                val endpoint = if (create) "$SUPABASE_URL/auth/v1/signup" else "$SUPABASE_URL/auth/v1/token?grant_type=password"
                val body = JSONObject().put("email",email).put("password",password).toString()
                val req = Request.Builder().url(endpoint).post(body.toRequestBody("application/json".toMediaType()))
                    .addHeader("apikey",SUPABASE_ANON_KEY).addHeader("Content-Type","application/json").build()
                val res = withContext(Dispatchers.IO){client.newCall(req).execute()}
                val txt=res.body?.string().orEmpty()
                if(!res.isSuccessful) throw Exception(JSONObject(txt).optString("msg",JSONObject(txt).optString("error_description","Authentication failed")))
                val obj=JSONObject(txt)
                accessToken=obj.optString("access_token")
                userId=JSONObject(obj.optString("user", "{}")).optString("id")
                if (accessToken.isNullOrBlank()) {
                    toast("Account created. Email verification ke baad login karein.")
                    return@launch
                }
                showHome()
            } catch(e:Exception){ toast(e.message ?: "Error") }
        }
    }

    private fun showHome() {
        val root=ScrollView(this)
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(24,24,24,24)}
        val title=TextView(this).apply{text="Ecommerce Master App";textSize=24f}
        val date=EditText(this).apply{text=SimpleDateFormat("yyyy-MM-dd",Locale.getDefault()).format(Date());hint="Date YYYY-MM-DD"}
        val platform=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,platforms)}
        val courier=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,couriers)}
        val count=EditText(this).apply{hint="Count";inputType=2}
        val saveO=Button(this).apply{text="Save Orders";setOnClickListener{insert("orders",date.text.toString(),platform.selectedItem.toString(),courier.selectedItem.toString(),count.text.toString())}}
        val saveR=Button(this).apply{text="Save Returns";setOnClickListener{insert("returns",date.text.toString(),platform.selectedItem.toString(),courier.selectedItem.toString(),count.text.toString())}}
        val refresh=Button(this).apply{text="Refresh / View Summary";setOnClickListener{loadSummary()}}
        val logout=Button(this).apply{text="Logout";setOnClickListener{accessToken=null;userId=null;showLogin()}}
        val out=TextView(this).apply{textSize=16f}
        box.addView(title);box.addView(date);box.addView(platform);box.addView(courier);box.addView(count)
        box.addView(saveO);box.addView(saveR);box.addView(refresh);box.addView(logout);box.addView(out)
        root.addView(box);setContentView(root)
        loadSummary(out)
    }

    private fun insert(type:String,date:String,platform:String,courier:String,countText:String){
        val count=countText.toIntOrNull()
        if(count==null||count<0||date.length!=10){toast("Date aur valid count dein.");return}
        val uid=userId ?: return
        scope.launch {
            try {
                val obj=JSONObject().put("user_id",uid).put("type",type).put("record_date",date)
                    .put("platform",platform).put("courier",if(type=="returns")courier else JSONObject.NULL).put("count",count)
                val req=Request.Builder().url("$SUPABASE_URL/rest/v1/records").post(obj.toString().toRequestBody("application/json".toMediaType()))
                    .addHeader("apikey",SUPABASE_ANON_KEY).addHeader("Authorization","Bearer $accessToken")
                    .addHeader("Prefer","return=minimal").build()
                val r=withContext(Dispatchers.IO){client.newCall(req).execute()}
                if(!r.isSuccessful) throw Exception(r.body?.string() ?: "Save failed")
                toast("$type saved");loadSummary()
            }catch(e:Exception){toast(e.message?:"Save error")}
        }
    }

    private fun loadSummary(target:TextView?=null){
        scope.launch {
            try{
                val req=Request.Builder().url("$SUPABASE_URL/rest/v1/records?select=type,platform,courier,count,record_date&order=record_date.desc")
                    .get().addHeader("apikey",SUPABASE_ANON_KEY).addHeader("Authorization","Bearer $accessToken").build()
                val r=withContext(Dispatchers.IO){client.newCall(req).execute()}
                if(!r.isSuccessful) throw Exception(r.body?.string()?:"Load failed")
                val arr=JSONArray(r.body?.string()?:"[]")
                val map=linkedMapOf<String,IntArray>()
                for(i in 0 until arr.length()){
                    val x=arr.getJSONObject(i);val p=x.optString("platform");val a=map.getOrPut(p){intArrayOf(0,0)}
                    if(x.optString("type")=="orders")a[0]+=x.optInt("count") else a[1]+=x.optInt("count")
                }
                val sb=StringBuilder("Cloud Summary (all saved records)\n\n")
                if(map.isEmpty())sb.append("No records yet.")
                else map.forEach{(p,a)->sb.append("$p: Orders ${a[0]} | Returns ${a[1]}\n")}
                target?.text=sb.toString() ?: run{Toast.makeText(this@MainActivity,sb.toString(),Toast.LENGTH_LONG).show()}
            }catch(e:Exception){toast(e.message?:"Load error")}
        }
    }

    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_LONG).show()
    override fun onDestroy(){scope.cancel();super.onDestroy()}
}
