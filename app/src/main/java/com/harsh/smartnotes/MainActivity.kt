package com.harsh.smartnotes

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.*
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import android.text.Editable
import android.text.TextWatcher
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date
import java.util.Locale

data class Note(var title: String, var body: String, var pinned: Boolean = false, var favorite: Boolean = false, var updated: Long = System.currentTimeMillis())

class MainActivity : Activity() {
    private lateinit var list: LinearLayout
    private lateinit var search: EditText
    private val notes = mutableListOf<Note>()
    private val prefs by lazy { getSharedPreferences("smart_notes", Context.MODE_PRIVATE) }
    private val keyAlias = "SmartNotesPinKey"
    private var dark = true
    private var unlocked = false
    private var themeName = "Lavender"
    private var customThemeUri: String? = null
    private val themePickerRequest = 9001
    private val accent get() = when (themeName) { "Ocean" -> Color.rgb(30,116,180); "Forest" -> Color.rgb(43,125,83); "Sunset" -> Color.rgb(202,91,66); "Rose" -> Color.rgb(184,78,116); else -> Color.rgb(103,80,164) }
    private val bg get() = Color.rgb(15,17,22)
    private val card get() = Color.rgb(28,31,39)
    private val primaryText get() = Color.rgb(245,246,250)
    private val secondary get() = Color.rgb(175,180,193)

    override fun onCreate(state: Bundle?) { super.onCreate(state); dark = true; prefs.edit().putBoolean("dark",true).apply(); themeName=prefs.getString("theme","Lavender")?:"Lavender"; customThemeUri=prefs.getString("custom_theme_uri",null); load(); if(hasPin()) pinScreen(false) else setupPin() }
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
    private fun root()=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(18),dp(20),dp(14));setBackgroundColor(bg)}
    private fun label(s:String,size:Float,bold:Boolean=false)=TextView(this).apply{text=s;textSize=size;setTextColor(primaryText);if(bold)typeface=Typeface.DEFAULT_BOLD}
    private fun rounded(color:Int,radius:Int=18)=GradientDrawable().apply{setColor(color);cornerRadius=dp(radius).toFloat()}
    private fun button(s:String)=Button(this).apply{text=s;textSize=14f;setTextColor(Color.WHITE);isAllCaps=false;background=rounded(accent,16);stateListAnimator=null;minHeight=dp(48);setPadding(dp(12),0,dp(12),0)}

    private fun home(){
        val r=root(); val top=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}; val titleBlock=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        titleBlock.addView(label("Smart Notes",30f,true)); titleBlock.addView(label("Simple. Private. Yours.",13f).apply{setTextColor(secondary);setPadding(0,dp(2),0,0)}); top.addView(titleBlock,LinearLayout.LayoutParams(0,-2,1f))
        top.addView(button("🌙").apply{textSize=20f;setOnClickListener{settings()}},LinearLayout.LayoutParams(dp(54),dp(50)))
        top.addView(button("🔒").apply{textSize=18f;setOnClickListener{changePin()}},LinearLayout.LayoutParams(dp(54),dp(50)).apply{leftMargin=dp(8)})
        top.addView(button("⚙").apply{textSize=19f;setOnClickListener{settings()}},LinearLayout.LayoutParams(dp(54),dp(50)).apply{leftMargin=dp(8)}); r.addView(top)
        search=EditText(this).apply{hint="🔍  Search your notes";setHintTextColor(secondary);setTextColor(primaryText);textSize=15f;setSingleLine(true);setPadding(dp(18),0,dp(18),0);background=rounded(card,18);addTextChangedListener(object:TextWatcher{override fun beforeTextChanged(s:CharSequence?,a:Int,b:Int,c:Int){};override fun onTextChanged(s:CharSequence?,a:Int,b:Int,c:Int){render(s?.toString()?:"")};override fun afterTextChanged(s:Editable?){} })}
        r.addView(search,LinearLayout.LayoutParams(-1,dp(56)).apply{topMargin=dp(18);bottomMargin=dp(14)}); val section=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}; section.addView(label("Your notes",19f,true),LinearLayout.LayoutParams(0,-2,1f)); section.addView(label("${notes.size} ${if(notes.size==1)"note" else "notes"}",13f).apply{setTextColor(secondary)}); r.addView(section,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(10)})
        val scroll=ScrollView(this); list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};scroll.addView(list);r.addView(scroll,LinearLayout.LayoutParams(-1,0,1f));r.addView(button("＋  New note").apply{textSize=16f;setOnClickListener{editor(null)}},LinearLayout.LayoutParams(-1,dp(54)).apply{topMargin=dp(10)});setContentView(r);render("")
    }

    private fun render(query:String){list.removeAllViews();val filtered=notes.filter{query.isBlank()||(it.title+" "+it.body).contains(query,true)}.sortedWith(compareByDescending<Note>{it.pinned}.thenByDescending{it.updated});if(filtered.isEmpty()){list.addView(label(if(notes.isEmpty())"📝\n\nNo notes yet\nCreate your first note below." else "🔎\n\nNo matching notes.",16f).apply{gravity=Gravity.CENTER;setTextColor(secondary);setPadding(0,dp(55),0,dp(20))},LinearLayout.LayoutParams(-1,dp(240)));return};filtered.forEach{n->val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(14),dp(16),dp(14));background=rounded(card,18);setOnClickListener{editor(n)}};val row=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL};row.addView(label((if(n.pinned)"📌 " else "")+(if(n.favorite)"★ " else "")+(n.title.ifBlank{"Untitled"}),17f,true),LinearLayout.LayoutParams(0,-2,1f));row.addView(label(DateFormat.getDateInstance(DateFormat.SHORT,Locale.getDefault()).format(Date(n.updated)),11f).apply{setTextColor(secondary)});box.addView(row);box.addView(label(n.body.ifBlank{"No content"},13f).apply{setTextColor(secondary);maxLines=3;setPadding(0,dp(8),0,0)});list.addView(box,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(10)})}}

    private fun editor(note:Note?){val r=root();val top=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL};top.addView(label(if(note==null)"New note" else "Edit note",25f,true),LinearLayout.LayoutParams(0,-2,1f));top.addView(button("Cancel").apply{setOnClickListener{home()}},LinearLayout.LayoutParams(dp(95),dp(48)));r.addView(top);val title=EditText(this).apply{hint="Title";setText(note?.title?:"");setHintTextColor(secondary);setTextColor(primaryText);textSize=20f;setSingleLine(true);background=rounded(card,16);setPadding(dp(16),0,dp(16),0)};val body=EditText(this).apply{hint="Write your note...";setText(note?.body?:"");setHintTextColor(secondary);setTextColor(primaryText);textSize=16f;gravity=Gravity.TOP;setPadding(dp(16),dp(16),dp(16),dp(16));background=rounded(card,16)};r.addView(title,LinearLayout.LayoutParams(-1,dp(58)).apply{topMargin=dp(20)});r.addView(body,LinearLayout.LayoutParams(-1,0,1f).apply{topMargin=dp(12);bottomMargin=dp(12)});val actions=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL};actions.addView(button("Save").apply{setOnClickListener{val t=title.text.toString().trim();val b=body.text.toString().trim();if(note==null){notes.add(Note(t,b))}else{note.title=t;note.body=b;note.updated=System.currentTimeMillis()};save();home()}},LinearLayout.LayoutParams(0,dp(54),1f));if(note!=null){actions.addView(button("Delete").apply{setOnClickListener{notes.remove(note);save();home()}},LinearLayout.LayoutParams(0,dp(54),1f).apply{leftMargin=dp(10)})};r.addView(actions);setContentView(r)}

    private fun settings(){val r=root();val head=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL};head.addView(label("Settings",27f,true),LinearLayout.LayoutParams(0,-2,1f));head.addView(button("Done").apply{setOnClickListener{home()}},LinearLayout.LayoutParams(dp(90),dp(48)));r.addView(head);r.addView(label("🌙 Dark mode • Always on",16f,true).apply{setPadding(0,dp(24),0,dp(16))});r.addView(label("Accent theme",15f,true));val names=listOf("Lavender","Ocean","Forest","Sunset","Rose");names.forEach{name->r.addView(button(if(themeName==name)"✓  $name" else name).apply{setOnClickListener{themeName=name;prefs.edit().putString("theme",name).apply();settings()}},LinearLayout.LayoutParams(-1,dp(50)).apply{topMargin=dp(8)})};r.addView(button("🖼  Personal image theme").apply{setOnClickListener{startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{type="image/*";addCategory(Intent.CATEGORY_OPENABLE);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)},themePickerRequest)}},LinearLayout.LayoutParams(-1,dp(52)).apply{topMargin=dp(18)});setContentView(r)}
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){super.onActivityResult(requestCode,resultCode,data);if(requestCode==themePickerRequest&&resultCode==RESULT_OK){data?.data?.let{uri->try{contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)}catch(_:Exception){};customThemeUri=uri.toString();prefs.edit().putString("custom_theme_uri",customThemeUri).apply();settings()}}}

    private fun keystoreKey():SecretKey{val ks=KeyStore.getInstance("AndroidKeyStore").apply{load(null)};val existing=ks.getKey(keyAlias,null)as? SecretKey;if(existing!=null)return existing;val generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");generator.init(KeyGenParameterSpec.Builder(keyAlias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setUserAuthenticationRequired(false).build());return generator.generateKey()}
    private fun encryptPin(pin:String):String{val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,keystoreKey());val encrypted=cipher.doFinal(pin.toByteArray(Charsets.UTF_8));return Base64.encodeToString(cipher.iv,Base64.NO_WRAP)+":"+Base64.encodeToString(encrypted,Base64.NO_WRAP)}
    private fun decryptPin(stored:String):String?=try{val p=stored.split(":");if(p.size!=2)return null;val iv=Base64.decode(p[0],Base64.NO_WRAP);val data=Base64.decode(p[1],Base64.NO_WRAP);val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,keystoreKey(),javax.crypto.spec.GCMParameterSpec(128,iv));String(cipher.doFinal(data),Charsets.UTF_8)}catch(_:Exception){null}
    private fun hasPin()=!prefs.getString("pin","").isNullOrBlank()
    private fun setupPin(){val r=root();val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_HORIZONTAL;setPadding(dp(24),dp(60),dp(24),dp(20))};box.addView(label("🔐",46f).apply{gravity=Gravity.CENTER});box.addView(label("Protect your notes",28f,true).apply{gravity=Gravity.CENTER;setPadding(0,dp(12),0,dp(8))});box.addView(label("Set a 4-digit PIN. It is encrypted with Android Keystore.",14f).apply{gravity=Gravity.CENTER;setTextColor(secondary);setPadding(0,0,0,dp(24))});val pin=EditText(this).apply{hint="••••";inputType=android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD;setTextColor(primaryText);setHintTextColor(secondary);textSize=26f;gravity=Gravity.CENTER;setSingleLine(true);background=rounded(card,18)};val confirm=EditText(this).apply{hint="Confirm PIN";inputType=android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD;setTextColor(primaryText);setHintTextColor(secondary);textSize=20f;gravity=Gravity.CENTER;setSingleLine(true);background=rounded(card,18)};box.addView(pin,LinearLayout.LayoutParams(-1,dp(58)).apply{bottomMargin=dp(12)});box.addView(confirm,LinearLayout.LayoutParams(-1,dp(58)).apply{bottomMargin=dp(20)});box.addView(button("Set PIN & Continue").apply{setOnClickListener{val a=pin.text.toString();val b=confirm.text.toString();if(a.length!=4||!a.all{it.isDigit()})Toast.makeText(this@MainActivity,"PIN must be exactly 4 digits",Toast.LENGTH_SHORT).show()else if(a!=b)Toast.makeText(this@MainActivity,"PINs do not match",Toast.LENGTH_SHORT).show()else{prefs.edit().putString("pin",encryptPin(a)).apply();unlocked=true;home()}}},LinearLayout.LayoutParams(-1,dp(54)));r.addView(box,LinearLayout.LayoutParams(-1,0,1f));setContentView(r)}
    private fun pinScreen(changing:Boolean){val r=root();val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_HORIZONTAL;setPadding(dp(24),dp(70),dp(24),dp(20))};box.addView(label("🔒",46f).apply{gravity=Gravity.CENTER});box.addView(label(if(changing)"Change PIN" else "Welcome back",28f,true).apply{gravity=Gravity.CENTER;setPadding(0,dp(12),0,dp(8))});box.addView(label(if(changing)"Choose a new 4-digit PIN." else "Enter your 4-digit PIN to unlock your notes.",14f).apply{gravity=Gravity.CENTER;setTextColor(secondary);setPadding(0,0,0,dp(24))});val pin=EditText(this).apply{hint="••••";inputType=android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD;setTextColor(primaryText);setHintTextColor(secondary);textSize=28f;gravity=Gravity.CENTER;setSingleLine(true);background=rounded(card,18)};box.addView(pin,LinearLayout.LayoutParams(-1,dp(62)).apply{bottomMargin=dp(20)});if(changing){val confirm=EditText(this).apply{hint="Confirm new PIN";inputType=android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD;setTextColor(primaryText);setHintTextColor(secondary);textSize=20f;gravity=Gravity.CENTER;setSingleLine(true);background=rounded(card,18)};box.addView(confirm,LinearLayout.LayoutParams(-1,dp(58)).apply{bottomMargin=dp(20)});box.addView(button("Update PIN").apply{setOnClickListener{val a=pin.text.toString();val b=confirm.text.toString();if(a.length!=4||!a.all{it.isDigit()})Toast.makeText(this@MainActivity,"PIN must be exactly 4 digits",Toast.LENGTH_SHORT).show()else if(a!=b)Toast.makeText(this@MainActivity,"PINs do not match",Toast.LENGTH_SHORT).show()else{prefs.edit().putString("pin",encryptPin(a)).apply();home()}}},LinearLayout.LayoutParams(-1,dp(54)))}else box.addView(button("Unlock").apply{setOnClickListener{val saved=prefs.getString("pin",null);if(saved!=null&&decryptPin(saved)==pin.text.toString()){unlocked=true;home()}else{pin.text.clear();Toast.makeText(this@MainActivity,"Incorrect PIN",Toast.LENGTH_SHORT).show()}}},LinearLayout.LayoutParams(-1,dp(54)));r.addView(box,LinearLayout.LayoutParams(-1,0,1f));setContentView(r);pin.requestFocus()}
    private fun changePin(){if(hasPin())pinScreen(true)else setupPin()}
    private fun save(){val arr=JSONArray();notes.forEach{n->arr.put(JSONObject().apply{put("title",n.title);put("body",n.body);put("pinned",n.pinned);put("favorite",n.favorite);put("updated",n.updated)})};prefs.edit().putString("data",arr.toString()).apply()}
    private fun load(){try{val arr=JSONArray(prefs.getString("data","[]"));for(i in 0 until arr.length()){val o=arr.getJSONObject(i);notes.add(Note(o.optString("title"),o.optString("body"),o.optBoolean("pinned"),o.optBoolean("favorite"),o.optLong("updated")))}}catch(_:Exception){}}
    override fun onBackPressed(){if(unlocked)super.onBackPressed()else pinScreen(false)}
}
