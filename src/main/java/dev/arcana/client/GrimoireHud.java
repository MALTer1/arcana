package dev.arcana.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.arcana.network.GrimoireNetwork;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public final class GrimoireHud {
    private static JsonObject state = new JsonObject();
    private static int tab, selectedSpell, selectedLoosePage;
    private static boolean removeMode;
    private static String notice = "";
    private static long noticeUntil;
    private GrimoireHud() {}

    public static void receiveState(String json) {
        try {
            state = JsonParser.parseString(json).getAsJsonObject();
            if (state.has("message")) { notice = state.get("message").getAsString(); noticeUntil = System.currentTimeMillis()+3500; }
            selectedSpell = Math.max(0, Math.min(selectedSpell, spells().size()-1));
            selectedLoosePage = Math.max(0, Math.min(selectedLoosePage, pages().size()-1));
        } catch (RuntimeException ignored) { notice = "Could not read grimoire pages."; noticeUntil=System.currentTimeMillis()+3500; }
    }
    public static void refresh() { send("refresh",""); }
    public static void previousPage() { if(tab==0) selectedSpell=Math.max(0,selectedSpell-1); else if(tab==1) selectedLoosePage=Math.max(0,selectedLoosePage-1); }
    public static void nextPage() { if(tab==0) selectedSpell=Math.min(Math.max(0,spells().size()-1),selectedSpell+1); else if(tab==1) selectedLoosePage=Math.min(Math.max(0,pages().size()-1),selectedLoosePage+1); }
    public static void nextTab() { tab=(tab+1)%4; }
    public static void previousTab() { tab=(tab+3)%4; }
    public static void toggleRemove() { removeMode=!removeMode; notice=removeMode?"Removal mode: press Use on a spell page.":"Removal mode off."; noticeUntil=System.currentTimeMillis()+3000; }
    public static void usePage() {
        if(tab==0&&!spells().isEmpty()) { JsonObject s=spells().get(selectedSpell).getAsJsonObject(); send(removeMode?"remove":"cast",s.get("name").getAsString()); }
        else if(tab==1&&!pages().isEmpty()) send("insert",pages().get(selectedLoosePage).getAsString());
    }
    private static void send(String a,String n) { MinecraftClient c=MinecraftClient.getInstance(); if(c.player!=null&&c.getNetworkHandler()!=null) ClientPlayNetworking.send(new GrimoireNetwork.ActionPayload(a,n)); }
    private static JsonArray spells() { return state.has("spells")&&state.get("spells").isJsonArray()?state.getAsJsonArray("spells"):new JsonArray(); }
    private static JsonArray pages() { return state.has("loosePages")&&state.get("loosePages").isJsonArray()?state.getAsJsonArray("loosePages"):new JsonArray(); }

    public static void render(DrawContext d,MinecraftClient c) {
        if(c.player==null)return;
        int sw=c.getWindow().getScaledWidth(), sh=c.getWindow().getScaledHeight();
        int w=Math.min(560,sw-24), h=Math.min(190,Math.max(145,sh/3)), x=(sw-w)/2, y=sh-h-5, mid=x+w/2;
        d.fill(x+3,y+9,mid+5,y+h,0xFF24102F); d.fill(mid-5,y+9,x+w-3,y+h,0xFF24102F);
        d.fill(x+8,y+3,mid+2,y+h-5,0xFFF3E6C9); d.fill(mid-2,y+3,x+w-8,y+h-5,0xFFF7EBD2);
        d.fill(mid-3,y+3,mid+3,y+h-5,0xFF9B7950); d.fill(x+8,y+3,x+13,y+h-5,0xFFB99A70); d.fill(x+w-13,y+3,x+w-8,y+h-5,0xFFB99A70);
        String tier=state.has("tier")?state.get("tier").getAsString():"Grimoire";
        d.drawCenteredTextWithShadow(c.textRenderer,tier,mid,y+8,0xFF4B3021);
        String[] labels={"SPELLS","PAGES","ARCANA","BOOK"};
        for(int i=0;i<4;i++){int tx=x+15+i*74; d.fill(tx,y-8,tx+68,y+5,i==tab?0xFF6B4380:0xFF4A2B5A); d.drawCenteredTextWithShadow(c.textRenderer,labels[i],tx+34,y-5,i==tab?0xFFF3E6C9:0xFFDDC9A9);}
        int lx=x+24, rx=mid+16, ty=y+30;
        if(tab==0){
            d.drawText(c.textRenderer,"SPELL INDEX",lx,ty,0xFF68482E,false);
            if(spells().isEmpty()){d.drawText(c.textRenderer,"This grimoire has no spells yet.",lx,ty+24,0xFF513B2A,false); d.drawText(c.textRenderer,"Add a page from the PAGES tab.",lx,ty+39,0xFF513B2A,false);}
            else{
                int start=Math.max(0,Math.min(selectedSpell-2,spells().size()-5));
                for(int i=start;i<Math.min(start+5,spells().size());i++){String n=spells().get(i).getAsJsonObject().get("name").getAsString();d.drawText(c.textRenderer,(i==selectedSpell?"▶ ":"  ")+n,lx,ty+18+(i-start)*17,i==selectedSpell?0xFF6B378C:0xFF513B2A,false);}
                JsonObject s=spells().get(selectedSpell).getAsJsonObject(); String n=s.get("name").getAsString();
                d.drawCenteredTextWithShadow(c.textRenderer,n,rx+100,ty,0xFF51321F);
                d.fill(rx+12,ty+19,rx+76,ty+83,0xFFE7D7B7); d.drawCenteredTextWithShadow(c.textRenderer,"✦",rx+44,ty+38,0xFF7545A0); d.drawCenteredTextWithShadow(c.textRenderer,"SPELL SIGIL",rx+44,ty+66,0xFF684D36);
                String behavior=s.has("behavior")?s.get("behavior").getAsString():"Unknown effect";
                d.drawText(c.textRenderer,behavior,rx+86,ty+22,0xFF513B2A,false);
                JsonArray a=s.has("components")?s.getAsJsonArray("components"):new JsonArray();
                d.drawText(c.textRenderer,"Components: "+a.size(),rx+86,ty+42,0xFF684D36,false);
                d.drawText(c.textRenderer,"Arcana cost: "+(10+a.size()*5),rx+86,ty+57,0xFF684D36,false);
                d.drawText(c.textRenderer,removeMode?"USE: remove page":"USE: cast spell",rx+10,y+h-22,0xFF6B378C,false);
            }
        } else if(tab==1){
            d.drawText(c.textRenderer,"UNBOUND SPELL PAGES",lx,ty,0xFF68482E,false);
            if(pages().isEmpty())d.drawText(c.textRenderer,"No loose pages in your inventory.",lx,ty+24,0xFF513B2A,false);
            else for(int i=0;i<Math.min(7,pages().size());i++){String n=pages().get(i).getAsString();d.drawText(c.textRenderer,(i==selectedLoosePage?"▶ ":"  ")+n,lx,ty+18+i*17,i==selectedLoosePage?0xFF6B378C:0xFF513B2A,false);}
            d.drawText(c.textRenderer,"USE: insert selected page",rx,ty+28,0xFF6B378C,false);
        } else if(tab==2){
            int v=state.has("arcana")?state.get("arcana").getAsInt():0,m=state.has("maxArcana")?state.get("maxArcana").getAsInt():100;
            d.drawText(c.textRenderer,"ARCANA RESERVE",lx,ty,0xFF68482E,false); d.fill(lx,ty+28,lx+180,ty+40,0xFFB9A88C); d.fill(lx,ty+28,lx+180*v/Math.max(1,m),ty+40,0xFF8051A0); d.drawText(c.textRenderer,v+" / "+m+" ARCANA",lx,ty+50,0xFF513B2A,false); d.drawText(c.textRenderer,"Your reserve restores over time.",rx,ty+30,0xFF513B2A,false);
        } else {
            d.drawText(c.textRenderer,"GRIMOIRE DETAILS",lx,ty,0xFF68482E,false);
            d.drawText(c.textRenderer,"Stored pages: "+spells().size()+" / "+(state.has("slots")?state.get("slots").getAsInt():0),lx,ty+24,0xFF513B2A,false);
            d.drawText(c.textRenderer,"Max components: "+(state.has("maxComponents")?state.get("maxComponents").getAsInt():0),lx,ty+42,0xFF513B2A,false);
        }
        if(System.currentTimeMillis()<noticeUntil&&!notice.isBlank())d.drawCenteredTextWithShadow(c.textRenderer,notice,sw/2,y-20,0xFFFFFFFF);
        d.drawCenteredTextWithShadow(c.textRenderer,"← / → PAGE     ↑ / ↓ TAB     ENTER USE     X REMOVE",sw/2,sh-9,0xFFFFFFFF);
    }
}