# 「踏上旅程！」成就奖励：发放一本拂晓附魔书
# 由 advancements/journey.json 的 rewards.function 调用，
# 命令源为获得成就的玩家本人（@s）。
# 注意：附魔书必须使用 StoredEnchantments 标签（而非 Enchantments），
# 否则铁砧读取不到书中附魔，无法将其附到武器上。
give @s minecraft:enchanted_book{StoredEnchantments:[{id:"enchantment_expansion:dawn",lvl:1}]} 1
