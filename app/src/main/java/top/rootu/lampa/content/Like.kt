package top.rootu.lampa.content

import com.google.gson.Gson
import top.rootu.lampa.App
import top.rootu.lampa.helpers.Prefs.CUB
import top.rootu.lampa.helpers.Prefs.FAV
import top.rootu.lampa.helpers.Prefs.histToRemove
import top.rootu.lampa.helpers.Prefs.likeToRemove
import top.rootu.lampa.helpers.Prefs.syncEnabled
import top.rootu.lampa.models.LampaCard

class Like : LampaProviderI() {

    override fun get(): ReleaseID {
        return ReleaseID(Like.get())
    }

    companion object {
        fun get(): List<LampaCard> {
            val lst = mutableListOf<LampaCard>()
            // CUB
            if (App.context.syncEnabled)
                App.context.CUB?.filter { it.type == LampaProvider.Like }?.forEach {
                    val card = Gson().fromJson(it.data, LampaCard::class.java)
                    card.fixCard()
                    lst.add(card)
                }
            // FAV (use ID to match KP_573840 etc)
            App.context.FAV?.card?.filter { App.context.FAV?.like?.contains(it.id.toString()) == true }
                ?.forEach {
                    it.fixCard() // not needed as don in FAV.get() but for sure
                    lst.add(it)
                }
            // exclude pending
            return lst.filter { !App.context.likeToRemove.contains(it.id.toString()) }
                .reversed()
        }
    }
}