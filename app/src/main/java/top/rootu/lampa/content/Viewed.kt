package top.rootu.lampa.content

import top.rootu.lampa.App
import top.rootu.lampa.helpers.Helpers.getJson
import top.rootu.lampa.helpers.Prefs.CUB
import top.rootu.lampa.helpers.Prefs.FAV
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
                    val card = getJson(bm.data, LampaCard::class.java)
                    card?.let {
                        it.fixCard()
                        lst.add(it)
                    }
                }
            // FAV (use ID to match KP_573840 etc)
            App.context.FAV?.card?.filter { App.context.FAV?.viewed?.contains(it.id.toString()) == true }
                ?.forEach { lst.add(it) }
            // exclude pending
            return lst.filter { !App.context.viewToRemove.contains(it.id.toString()) }
                .reversed()
        }
    }
}