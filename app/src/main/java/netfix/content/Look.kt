package netfix.content

import netfix.App
import netfix.helpers.Prefs.CUB
import netfix.helpers.Prefs.FAV
import netfix.helpers.Prefs.lookToRemove
import netfix.helpers.Prefs.syncEnabled
import netfix.models.NetfixCard

class Look : NetfixProviderI() {

    override fun get(): ReleaseID {
        return ReleaseID(Look.get())
    }

    companion object {
        fun get(): List<NetfixCard> {
            val lst = mutableListOf<NetfixCard>()
            // CUB
            if (App.context.syncEnabled) {
                App.context.CUB
                    ?.filter { it.type == NetfixProvider.LOOK }
                    ?.reversed()
                    ?.mapNotNull { it.data?.apply { fixCard() } }
                    ?.let { lst.addAll(it) }
            }
            // FAV
            App.context.FAV?.card
                ?.filter { App.context.FAV?.look?.contains(it.id.toString()) == true }
                ?.let { lst.addAll(it) }
            // Exclude pending and reverse the final list
            return lst
                .filterNot { App.context.lookToRemove.contains(it.id.toString()) }
                .reversed()
        }
    }
}