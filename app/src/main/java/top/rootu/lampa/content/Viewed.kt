package top.rootu.lampa.content

import com.google.gson.Gson
import top.rootu.lampa.App
import top.rootu.lampa.helpers.Prefs.CUB
import top.rootu.lampa.helpers.Prefs.syncEnabled
import top.rootu.lampa.helpers.Prefs.viewToRemove
import top.rootu.lampa.models.LampaCard

class Viewed : LampaProviderI() {

    override fun get(): ReleaseID {
        return ReleaseID(Viewed.get())
    }

    companion object {
        fun get(): List<LampaCard> {
            val lst = mutableListOf<LampaCard>()
            // CUB
            if (App.context.syncEnabled)
                App.context.CUB?.filter { it.type == LampaProvider.VIEW }?.forEach { bm ->
                    val card = try {
                        Gson().fromJson(bm.data, LampaCard::class.java)
                    } catch (e: Exception) {
                        null
                    }
                    card?.let {
                        it.fixCard()
                        lst.add(it)
                    }
                }
            // exclude pending
            return lst.filter { !App.context.viewToRemove.contains(it.id.toString()) }
                .reversed()
        }
    }
}