package com.observateurbasket

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.*
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

data class Situation(
    val period: Int,
    val type: String,
    val text: String,
    val actors: List<CourtActor>
)

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var title: TextView
    private val prefs by lazy { getSharedPreferences("observateur", MODE_PRIVATE) }
    private val situations = mutableListOf<Situation>()
    private var currentPeriod = 1
    private var currentType = ""
    private var speech: SpeechRecognizer? = null
    private var speechText = ""
    private var matchName = ""
    private var teamA = ""
    private var teamB = ""
    private var level = ""
    private var ref1 = ""
    private var ref2 = ""
    private val date = SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE).format(Date())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showHome()
    }

    private fun base(titleText: String): LinearLayout {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20,20,20,20)
            setBackgroundColor(Color.rgb(248,249,251))
        }
        title = TextView(this).apply {
            text = titleText
            textSize = 25f
            setTextColor(Color.rgb(20,35,50))
            setPadding(0,0,0,18)
        }
        root.addView(title)
        setContentView(root)
        return root
    }

    private fun btn(text:String, action:()->Unit): Button =
        Button(this).apply { this.text=text; textSize=17f; setOnClickListener{action()} }

    private fun showHome() {
        val r=base("OBSERVATEUR BASKET")
        r.addView(btn("➕ Nouveau match"){showAdmin()})
        r.addView(btn("📂 Ouvrir les anciens dossiers"){showArchives()})
    }

    private fun showAdmin() {
        val r=base("Administration du match")
        val match=EditText(this); match.hint="Nom du match (ex. Vénissieux - Andrézieux)"
        val a=EditText(this); a.hint="Équipe A"
        val b=EditText(this); b.hint="Équipe B"
        val niv=EditText(this); niv.hint="Niveau (NF3, R2, N3...)"
        val r1=EditText(this); r1.hint="Arbitre 1 - prénom + nom"
        val r2=EditText(this); r2.hint="Arbitre 2 - prénom + nom"
        listOf(match,a,b,niv,r1,r2).forEach{r.addView(it)}
        r.addView(btn("💾 Enregistrer le match"){ 
            matchName=match.text.toString().ifBlank{"Match sans nom"}
            teamA=a.text.toString(); teamB=b.text.toString(); level=niv.text.toString()
            ref1=r1.text.toString(); ref2=r2.text.toString()
            saveMeta()
            showStart()
        })
        r.addView(btn("Retour"){showHome()})
    }

    private fun showStart() {
        val r=base("Match : $matchName")
        r.addView(TextView(this).apply{text="A : $teamA   |   B : $teamB\n$level\nR1 : $ref1\nR2 : $ref2"; textSize=16f})
        r.addView(btn("▶ DÉBUT DE MATCH"){showMatch()})
        r.addView(btn("Retour"){showHome()})
    }

    private fun showMatch() {
        val r=base("Période $currentPeriod — Observation")
        r.addView(btn("🟥 FAUTE"){startSituation("FAUTE")})
        r.addView(btn("🟨 VIOLATION"){startSituation("VIOLATION")})
        r.addView(btn("🟦 MÉCA"){startSituation("MÉCA")})
        r.addView(btn("🟪 GESTION"){startSituation("GESTION")})
        r.addView(btn("⚪ AUTRE"){startSituation("AUTRE")})
        r.addView(Space(this))
        val fin=btn("🏁 FIN DE QUART-TEMPS"){startPeriodDebrief()}
        r.addView(fin)
        r.addView(btn("📋 Voir mes notes"){showNotes()})
        r.addView(btn("⏸ Retour accueil"){showHome()})
    }

    private fun startSituation(type:String) {
        currentType=type
        speechText=""
        val r=base("$type — Dictée")
        val status=TextView(this).apply{text="🎙️ Le micro est ouvert.\nParle naturellement, puis appuie sur « J'AI FINI »."; textSize=18f}
        val edit=EditText(this).apply{minLines=5; textSize=18f; hint="La dictée apparaîtra ici..."}
        r.addView(status); r.addView(edit)
        r.addView(btn("⏹ J'AI FINI — enregistrer"){ 
            stopSpeech()
            speechText=edit.text.toString()
            showCourtForSituation(speechText)
        })
        r.addView(btn("Annuler"){stopSpeech();showMatch()})
        startSpeech(edit,status)
    }

    private fun startSpeech(edit:EditText,status:TextView) {
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this,arrayOf(Manifest.permission.RECORD_AUDIO),55)
        }
        if(!SpeechRecognizer.isRecognitionAvailable(this)){
            status.text="Micro non disponible. Tu peux quand même écrire."
            return
        }
        speech=SpeechRecognizer.createSpeechRecognizer(this)
        speech?.setRecognitionListener(object:RecognitionListener{
            override fun onReadyForSpeech(p:Bundle?){status.text="🎙️ J'écoute… tu peux parler. Appuie sur J'AI FINI quand tu as terminé."}
            override fun onBeginningOfSpeech(){ }
            override fun onRmsChanged(r:Float){}
            override fun onBufferReceived(b:ByteArray?){}
            override fun onEndOfSpeech(){status.text="⏸️ Pause détectée. Tu peux continuer à parler ou appuyer sur J'AI FINI."}
            override fun onError(error:Int){ if(error!=SpeechRecognizer.ERROR_CLIENT) status.text="🎙️ Dictée interrompue. Tu peux continuer dans la zone de texte." }
            override fun onResults(results:Bundle){
                val arr=results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if(!arr.isNullOrEmpty()){ edit.append(if(edit.text.isNotEmpty()) " " else "")
                    edit.append(arr[0]) }
                // Restart automatically after a normal pause so the user does not have to tap again.
                if(!isFinishing) try { speech?.startListening(recIntent()) } catch(_:Exception){}
            }
            override fun onPartialResults(p:Bundle){}
            override fun onEvent(t:Int,p:Bundle?){}
        })
        try { speech?.startListening(recIntent()) } catch(_:Exception){}
    }

    private fun recIntent()=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply{
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE,Locale.FRANCE.toLanguageTag())
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1)
    }

    private fun stopSpeech(){ try{speech?.stopListening();speech?.destroy()}catch(_:Exception){}; speech=null }

    private fun showCourtForSituation(text:String) {
        stopSpeech()
        val r=base("Positionner la situation")
        r.addView(TextView(this).apply{text="Déplace les acteurs sur le terrain. Les joueurs détectés dans ta dictée sont proposés automatiquement."; textSize=16f})
        val court=CourtView(this)
        court.layoutParams=LinearLayout.LayoutParams(-1,0,1f)
        val labels=Regex("\\b(?:A|B)\\d{1,2}\\b",RegexOption.IGNORE_CASE).findAll(text).map{it.value.uppercase()}.toMutableList()
        labels.add("R1"); labels.add("R2")
        r.addView(court)
        court.setActors(labels.distinct())
        val actorsText=TextView(this).apply{text="Acteurs : ${labels.distinct().joinToString(", ")}"; textSize=14f}
        r.addView(actorsText)
        r.addView(btn("✓ FIN — enregistrer la situation"){
            situations.add(Situation(currentPeriod,currentType,text,court.getActors()))
            persistSituations()
            showMatch()
        })
        r.addView(btn("Retour dictée"){showCourtForSituation(text)})
    }

    private fun startPeriodDebrief() {
        stopSpeech()
        val r=base("Fin de période $currentPeriod — Ressenti")
        val status=TextView(this).apply{text="🎙️ Dicte ton bilan de la période puis appuie sur J'AI FINI.";textSize=18f}
        val edit=EditText(this).apply{minLines=7;textSize=18f;hint="Bilan de la période…"}
        r.addView(status);r.addView(edit)
        r.addView(btn("⏹ J'AI FINI — enregistrer le bilan"){
            stopSpeech()
            saveDebrief(currentPeriod,edit.text.toString())
            if(currentPeriod>=4) showFinalReport() else { currentPeriod++; showMatch() }
        })
        r.addView(btn("Annuler"){stopSpeech();showMatch()})
        startSpeech(edit,status)
    }

    private fun showNotes() {
        val r=base("Mes notes — $matchName")
        if(situations.isEmpty()) r.addView(TextView(this).apply{text="Aucune situation enregistrée.";textSize=18f})
        situations.forEachIndexed{idx,s->
            r.addView(TextView(this).apply{
                text="${idx+1}. Q${s.period} • ${s.type}\n${s.text}\nActeurs : ${s.actors.joinToString{it.label}}"
                textSize=16f;setPadding(0,10,0,14)
            })
        }
        r.addView(btn("Retour"){showMatch()})
    }

    private fun showFinalReport() {
        val r=base("FIN DU MATCH — Rapport")
        r.addView(TextView(this).apply{
            text="Toutes les situations et les 4 bilans de période sont enregistrés.\n\nLa prochaine étape est la synthèse IA."
            textSize=18f
        })
        r.addView(btn("🤖 Générer la synthèse IA"){showAiDemo()})
        r.addView(btn("🖨️ Rapport papier"){showPrintableReport()})
        r.addView(btn("📋 Voir toutes les notes"){showNotes()})
        r.addView(btn("Accueil"){showHome()})
    }

    private fun showAiDemo() {
        val r=base("Synthèse d'observation")
        val sb=StringBuilder()
        sb.append("SYNTHÈSE D'OBSERVATION — $matchName\n\n")
        sb.append("Niveau : $level\nR1 : $ref1\nR2 : $ref2\n\n")
        sb.append("Situations observées : ${situations.size}\n\n")
        sb.append("Analyse structurée :\n")
        sb.append("• Décisions et lecture du jeu : à analyser à partir des situations saisies.\n")
        sb.append("• Mécanique : à croiser avec les positions R1/R2.\n")
        sb.append("• Gestion : à croiser avec les situations de gestion.\n")
        sb.append("• Points positifs : à faire ressortir dans les bilans de période.\n")
        sb.append("• Axes de travail : à hiérarchiser selon la récurrence et l'impact.\n\n")
        sb.append("IMPORTANT : cette V2 prépare toutes les données pour une véritable analyse IA. La connexion sécurisée à ChatGPT sera ajoutée dans l'étape suivante plutôt que de mettre une clé API directement dans l'application.")
        r.addView(ScrollView(this).apply{addView(TextView(this@MainActivity).apply{text=sb.toString();textSize=17f})},LinearLayout.LayoutParams(-1,0,1f))
        r.addView(btn("Retour au rapport"){showFinalReport()})
    }

    private fun showPrintableReport() {
        val text=buildReport()
        val send=Intent(Intent.ACTION_SEND).apply{
            type="text/plain";putExtra(Intent.EXTRA_SUBJECT,"Rapport observateur — $matchName");putExtra(Intent.EXTRA_TEXT,text)
        }
        startActivity(Intent.createChooser(send,"Partager / imprimer le rapport"))
    }

    private fun buildReport():String {
        val sb=StringBuilder()
        sb.append("FICHE DE PRISE DE NOTES — OBSERVATEUR\n")
        sb.append("$matchName — $date\n$teamA / $teamB — $level\nR1 : $ref1\nR2 : $ref2\n\n")
        situations.forEachIndexed{ i,s->
            sb.append("Situation ${i+1} — Q${s.period} — ${s.type}\n")
            sb.append("${s.text}\n")
            sb.append("Acteurs : ${s.actors.joinToString{it.label}}\n\n")
        }
        return sb.toString()
    }

    private fun saveMeta(){
        prefs.edit().putString("matchName",matchName).putString("teamA",teamA).putString("teamB",teamB)
            .putString("level",level).putString("ref1",ref1).putString("ref2",ref2).apply()
    }
    private fun persistSituations(){
        val arr=JSONArray()
        situations.forEach{s->
            val o=JSONObject().put("period",s.period).put("type",s.type).put("text",s.text)
            val aa=JSONArray()
            s.actors.forEach{a->aa.put(JSONObject().put("label",a.label).put("x",a.x).put("y",a.y).put("kind",a.kind))}
            o.put("actors",aa);arr.put(o)
        }
        prefs.edit().putString("situations",arr.toString()).apply()
    }
    private fun saveDebrief(period:Int,text:String){prefs.edit().putString("debrief_$period",text).apply()}
    private fun loadMeta(){
        matchName=prefs.getString("matchName","")?:"";teamA=prefs.getString("teamA","")?:""
        teamB=prefs.getString("teamB","")?:"";level=prefs.getString("level","")?:""
        ref1=prefs.getString("ref1","")?:"";ref2=prefs.getString("ref2","")?:""
    }
    private fun showArchives(){
        loadMeta()
        val r=base("Anciens dossiers")
        if(matchName.isBlank()) r.addView(TextView(this).apply{text="Aucun dossier local pour le moment.";textSize=18f})
        else r.addView(btn("📂 $matchName — $date"){loadSituations();currentPeriod=4;showFinalReport()})
        r.addView(btn("Retour"){showHome()})
    }
    private fun loadSituations(){
        situations.clear()
        val arr=JSONArray(prefs.getString("situations","[]"))
        for(i in 0 until arr.length()){
            val o=arr.getJSONObject(i);val aa=o.getJSONArray("actors");val actors=mutableListOf<CourtActor>()
            for(j in 0 until aa.length()){val a=aa.getJSONObject(j);actors.add(CourtActor(a.getString("label"),a.getDouble("x").toFloat(),a.getDouble("y").toFloat(),a.getInt("kind")))}
            situations.add(Situation(o.getInt("period"),o.getString("type"),o.getString("text"),actors))
        }
    }
    override fun onDestroy(){stopSpeech();super.onDestroy()}
}
