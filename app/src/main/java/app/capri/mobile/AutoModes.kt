package app.capri.mobile

// Automatic modes: she changes mode by herself when your day changes (you open Maps, a meeting app, the battery runs low, it gets late)
// and puts your own mode back when that is over. Off by default. Pure decision logic (no Android), so it is unit tested;
// the service feeds it the clock, the battery and the app in front once a minute.
object AutoModes {
    class Input(val hour: Int, val battery: Int, val charging: Boolean, val app: String)
    class Rules(val nightFrom: Int = 23, val nightTo: Int = 6, val lowBattery: Int = 15, val night: Boolean = true, val apps: Boolean = true, val battery: Boolean = true)

    private val driving = listOf("com.google.android.apps.maps", "com.waze", "com.here.app.maps", "com.sygic.aura", "com.android.car", "com.google.android.projection.gearhead", "com.ubercab.driver", "sa.careem", "com.careem.acma")
    private val meeting = listOf("us.zoom.videomeetings", "com.google.android.apps.meetings", "com.microsoft.teams", "com.cisco.webex.meetings", "com.skype.raider", "com.google.android.apps.classroom", "com.microsoft.teams.classes")

    fun inWindow(hour: Int, from: Int, to: Int): Boolean = if (from == to) false else if (from < to) hour in from until to else hour >= from || hour < to

    /** The mode the situation asks for, or null to leave things alone. The app in front beats the battery, which beats the clock. */
    fun wanted(i: Input, r: Rules = Rules()): String? {
        if (r.apps) {
            if (i.app in meeting) return "meeting"
            if (i.app in driving) return "drive"
        }
        if (r.battery && !i.charging && i.battery in 0..r.lowBattery) return "battery"
        if (r.night && inWindow(i.hour, r.nightFrom, r.nightTo)) return "night"
        return null
    }

    /** What to do now. apply = switch to this mode; restore = put the user's own mode back. */
    class Step(val apply: String?, val restore: Boolean, val forget: Boolean = false)
    private val NONE = Step(null, false)

    /**
     * cur = the saved mode now; autoBy = the mode she switched to by herself ("" if none); wanted = what [wanted] says.
     * A mode you picked by hand is never overridden while you are on it: if cur is not what she set, she forgets her switch.
     */
    fun step(cur: String, autoBy: String, wanted: String?): Step {
        if (autoBy.isEmpty()) return if (wanted != null && wanted != cur && manualOk(cur, wanted)) Step(wanted, false) else NONE
        if (cur != autoBy) return Step(null, false, true)
        return when {
            wanted == autoBy -> NONE
            wanted == null -> Step(null, true)
            else -> Step(wanted, false)
        }
    }
    // do not drag you out of a mode that is already stricter than what is wanted (meeting/drive are never replaced by night or battery)
    private fun manualOk(cur: String, wanted: String): Boolean = !(cur in listOf("meeting", "drive", "game") && wanted in listOf("night", "battery"))
}
