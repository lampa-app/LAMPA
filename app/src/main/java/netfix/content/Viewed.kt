package netfix.content

import netfix.App
import netfix.helpers.Prefs.CUB
import netfix.helpers.Prefs.FAV
import netfix.helpers.Prefs.syncEnabled
import netfix.helpers.Prefs.viewToRemove
import netfix.models.NetfixCard

class Viewed : NetfixProviderI() {

    override fun get(): ReleaseID {
        return ReleaseID(Viewed.get())
    }

    companion object {
        fun get(): List<NetfixCard> {
            val lst = mutableListOf<NetfixCard>()
            // CUB
            if (App.context.syncEnabled) {
                App.context.CUB
                    ?.filter { it.type == NetfixProvider.VIEW }
                    ?.reversed()
                    ?.mapNotNull { it.data?.apply { fixCard() } }
                    ?.let { lst.addAll(it) }
            }
            // FAV
            App.context.FAV?.card
                ?.filter { App.context.FAV?.viewed?.contains(it.id.toString()) == true }
                ?.let { lst.addAll(it) }
            // Exclude pending and reverse the final list
            return lst
                .filterNot { App.context.viewToRemove.contains(it.id.toString()) }
                .reversed()
        }
    }
}