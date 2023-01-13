package at.hannibal2.skyhanni.features.nether.reputationhelper.dailyquest

import at.hannibal2.skyhanni.data.HyPixelData
import at.hannibal2.skyhanni.data.IslandType
import at.hannibal2.skyhanni.events.GuiContainerEvent
import at.hannibal2.skyhanni.events.LorenzChatEvent
import at.hannibal2.skyhanni.events.ProfileApiDataLoadedEvent
import at.hannibal2.skyhanni.features.nether.reputationhelper.CrimsonIsleReputationHelper
import at.hannibal2.skyhanni.features.nether.reputationhelper.dailyquest.quest.*
import at.hannibal2.skyhanni.utils.InventoryUtils.getInventoryName
import at.hannibal2.skyhanni.utils.ItemUtils.getLore
import at.hannibal2.skyhanni.utils.ItemUtils.name
import at.hannibal2.skyhanni.utils.LorenzColor
import at.hannibal2.skyhanni.utils.LorenzUtils
import at.hannibal2.skyhanni.utils.RenderUtils.highlight
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.inventory.GuiChest
import net.minecraft.inventory.ContainerChest
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent

class DailyQuestHelper(private val reputationHelper: CrimsonIsleReputationHelper) {

    val quests = mutableListOf<Quest>()
    private var tick = 0

    private val loader = QuestLoader(this)

    private val sacksCache = mutableMapOf<String, Long>()

    @SubscribeEvent
    fun onTick(event: TickEvent.ClientTickEvent) {
        if (!HyPixelData.skyBlock) return
        if (LorenzUtils.skyBlockIsland != IslandType.CRIMSON_ISLE) return
        tick++
        if (tick % 3 == 0) {
            loader.checkInventory()
        }
        if (tick % 60 == 0) {
            checkInventoryForFetchItem()
        }

        if (tick % 60 == 0) {
            loader.loadFromTabList()

            if (quests.size != 5) {
                quests.clear()
                LorenzUtils.chat("§e[SkyHanni] Reset Quests.")
            }
        }
    }

    fun update() {
        reputationHelper.update()
    }

    @SubscribeEvent
    fun onBackgroundDrawn(event: GuiContainerEvent.BackgroundDrawnEvent) {
        if (!HyPixelData.skyBlock) return
        if (LorenzUtils.skyBlockIsland != IslandType.CRIMSON_ISLE) return
        if (event.gui !is GuiChest) return
        val chest = event.gui.inventorySlots as ContainerChest
        val chestName = chest.getInventoryName()

        if (chestName == "Challenges") {
            if (LorenzUtils.skyBlockArea != "Dojo") return
            val dojoQuest = getQuest<DojoQuest>() ?: return
            if (dojoQuest.state != QuestState.ACCEPTED) return

            for (slot in chest.inventorySlots) {
                if (slot == null) continue
                if (slot.slotNumber != slot.slotIndex) continue
                val stack = slot.stack ?: continue
                val itemName = stack.name ?: continue

                if (itemName.contains(dojoQuest.dojoName)) {
                    slot highlight LorenzColor.AQUA
                }
            }
        }
        if (chestName == "Sack of Sacks") {
            val fetchQuest = getQuest<FetchQuest>() ?: return
            if (fetchQuest.state != QuestState.ACCEPTED) return

            val fetchItem = fetchQuest.itemName
            for (slot in chest.inventorySlots) {
                if (slot == null) continue
                if (slot.slotNumber != slot.slotIndex) continue
                val stack = slot.stack ?: continue
                if (stack.getLore().any { it.contains(fetchItem) }) {
                    slot highlight LorenzColor.AQUA
                }
            }
        }
        if (chestName.contains("Nether Sack")) {
            val fetchQuest = getQuest<FetchQuest>() ?: return
            if (fetchQuest.state != QuestState.ACCEPTED) return

            val fetchItem = fetchQuest.itemName
            for (slot in chest.inventorySlots) {
                if (slot == null) continue
                if (slot.slotNumber != slot.slotIndex) continue
                val stack = slot.stack ?: continue
                val itemName = stack.name ?: continue

                if (itemName.contains(fetchItem)) {
                    slot highlight LorenzColor.AQUA
                }
            }
        }
    }

    @SubscribeEvent
    fun onChat(event: LorenzChatEvent) {
        if (!HyPixelData.skyBlock) return
        if (LorenzUtils.skyBlockIsland != IslandType.CRIMSON_ISLE) return

        val message = event.message
        if (message == "§aYou completed your Dojo quest! Visit the Town Board to claim the rewards.") {
            val dojoQuest = getQuest<DojoQuest>() ?: return
            dojoQuest.state = QuestState.READY_TO_COLLECT
            update()
        }
    }

    private inline fun <reified T : Quest> getQuest() = quests.filterIsInstance<T>().firstOrNull()

    private fun checkInventoryForFetchItem() {
        val fetchQuest = getQuest<FetchQuest>() ?: return
        if (fetchQuest.state != QuestState.ACCEPTED && fetchQuest.state != QuestState.READY_TO_COLLECT) return

        val itemName = fetchQuest.itemName

        val player = Minecraft.getMinecraft().thePlayer
        var count = 0
        for (stack in player.inventory.mainInventory) {
            if (stack == null) continue
            val name = stack.name ?: continue
            if (name.contains(itemName)) {
                count += stack.stackSize
            }
        }

        val needAmount = fetchQuest.needAmount
        if (count > needAmount) {
            count = needAmount
        }
        if (fetchQuest.haveAmount == count) return

        fetchQuest.haveAmount = count
        fetchQuest.state = if (count == needAmount) QuestState.READY_TO_COLLECT else QuestState.ACCEPTED
        update()
    }

    @SubscribeEvent
    fun onProfileDataLoad(event: ProfileApiDataLoadedEvent) {
        val profileData = event.profileData
        val sacks = profileData["sacks_counts"]?.asJsonObject ?: return

        sacksCache.clear()

        for ((name, v) in sacks.entrySet()) {
            val amount = v.asLong
            sacksCache[name] = amount
        }
        update()
    }

    fun renderAllQuests(display: MutableList<String>) {
        val done = quests.count { it.state == QuestState.COLLECTED }
//        val sneaking = Minecraft.getMinecraft().thePlayer.isSneaking
//        if (done != 5 || sneaking) {
        if (done != 5) {
            display.add("Daily Quests (collected $done/5)")
            for (quest in quests) {
//                if (!sneaking) {
//                    if (quest.state == QuestState.COLLECTED) {
//                        continue
//                    }
//                }
                display.add(renderQuest(quest))
            }
        }
    }

    private fun renderQuest(quest: Quest): String {
        val type = quest.category.displayName
        val state = quest.state.displayName
        val stateColor = quest.state.color
        val displayName = quest.displayName

        val multipleText = if (quest is ProgressQuest && quest.state != QuestState.COLLECTED) {
            val haveAmount = quest.haveAmount
            val needAmount = quest.needAmount
            " §e$haveAmount§8/§e$needAmount"
        } else {
            ""
        }

        val sacksText = if (quest is FetchQuest && quest.state != QuestState.COLLECTED) {
            val name = quest.itemName.uppercase()
            val amount = sacksCache.getOrDefault(name, 0)
            val needAmount = quest.needAmount
            val amountFormat = LorenzUtils.formatInteger(amount)
            val color = if (amount >= needAmount) {
                "§a"
            } else {
                "§c"
            }
            " §f($color$amountFormat §fin sacks)"
        } else {
            ""
        }
        return "$stateColor[$state] §f$type: §f$displayName$multipleText$sacksText"
    }
}