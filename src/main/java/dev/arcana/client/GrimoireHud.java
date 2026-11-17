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
            selectedSpell = Math.max(0, Math.min(selectedSpell, Math.max(0,spells().size()-1)));
            selectedLoosePage = Math.max(0, Math.min(selectedLoosePage, Math.max(0,pages().size()-1)));
        } catch (RuntimeException ignored) { notice = "Could not read grimoire pages."; noticeUntil=System.currentTimeMillis()+3500; }
    }
    public static void refresh() { send("refresh",""); }
    public static void previousPage() { if(tab==0) selectedSpell=Math.max(0,selectedSpell-1); else if(tab==1) selectedLoosePage=Math.max(0,selectedLoosePage-1); }
    public static void nextPage() { if(tab==0) selectedSpell=Math.min(Math.max(0,spells().size()-1),selectedSpell+1); else if(tab==1) selectedLoosePage=Math.min(Math.max(0,pages().size()-1),selectedLoosePage+1); }
    public static void nextTab() { tab=(tab+1)%4; }
    public static void previousTab() { tab=(tab+3)%4; }
    public static void toggleRemove() { removeMode=!removeMode; notice=removeMode?"Removal mode: select a spell and press Use.":"Removal mode off."; noticeUntil=System.currentTimeMillis()+3000; }
    public static void usePage() {
        if(tab==0&&!spells().isEmpty()) { JsonObject s=spells().get(selectedSpell).getAsJsonObject(); send(removeMode?"remove":"cast",s.get("name").getAsString()); }
        else if(tab==1&&!pages().isEmpty()) send("insert",pages().get(selectedLoosePage).getAsString());
    }
    private static void send(String a,String n) { MinecraftClient c=MinecraftClient.getInstance(); if(c.player!=null&&c.getNetworkHandler()!=null) ClientPlayNetworking.send(new GrimoireNetwork.ActionPayload(a,n)); }
    private static JsonArray spells() { return state.has("spells")&&state.get("spells").isJsonArray()?state.getAsJsonArray("spells"):new JsonArray(); }
    private static JsonArray pages() { return state.has("loosePages")&&state.get("loosePages").isJsonArray()?state.getAsJsonArray("loosePages"):new JsonArray(); }

    private static void line(DrawContext d, MinecraftClient c, String text, int x, int y, int color) {
        d.drawTextWithShadow(c.textRenderer, text, x, y, color);
    }
    private static void wrapped(DrawContext d, MinecraftClient c, String text, int x, int y, int width, int color, int maxLines) {
        if(text==null||text.isBlank()) return;
        StringBuilder current=new StringBuilder();
        int row=0;
        for(String word:text.split("\\s+")) {
            String candidate=current.length()==0?word:current+" "+word;
            if(c.textRenderer.getWidth(candidate)>width && current.length()>0) {
                line(d,c,current.toString(),x,y+row*11,color);
                row++;
                if(row>=maxLines) return;
                current.setLength(0);
                current.append(word);
            } else {
                if(current.length()>0) current.append(' ');
                current.append(word);
            }
        }
        if(current.length()>0&&row<maxLines) line(d,c,current.toString(),x,y+row*11,color);
    }

    public static void render(DrawContext d,MinecraftClient c) {
        if(c.player==null)return;
        int sw=c.getWindow().getScaledWidth(), sh=c.getWindow().getScaledHeight();
        int w=Math.min(900,sw-24), h=Math.min(226,Math.max(176,sh/3)), x=(sw-w)/2, y=sh-h-112, mid=x+w/2;
        // A clear leather border and two equally sized parchment pages.
        d.fill(x+2,y+8,mid+5,y+h,0xFF170D20); d.fill(mid-5,y+8,x+w-2,y+h,0xFF170D20);
        d.fill(x,y+4,mid+2,y+h-3,0xFF4B245F); d.fill(mid-2,y+4,x+w,y+h-3,0xFF4B245F);
        d.fill(x+7,y+8,mid-8,y+h-8,0xFFFFF1D5); d.fill(mid+8,y+8,x+w-7,y+h-8,0xFFFFF1D5);
        d.fill(x+10,y+11,mid-12,y+h-11,0xFFF4E4C3); d.fill(mid+12,y+11,x+w-10,y+h-11,0xFFF7EBD4);
        d.fill(mid-5,y+5,mid+5,y+h-6,0xFF8C6745);
        d.fill(mid-1,y+9,mid+1,y+h-10,0xFF5D422C);
        d.fill(x+7,y+8,x+12,y+h-8,0xFFB18A5C); d.fill(x+w-12,y+8,x+w-7,y+h-8,0xFFB18A5C);

        String tier=state.has("tier")?state.get("tier").getAsString():"Grimoire";
        d.drawCenteredTextWithShadow(c.textRenderer,tier,mid,y+12,0xFF392318);
        String[] labels={"SPELLS","PAGES","ARCANA","BOOK"};
        int tabWidth=Math.min(72,(w-32)/4);
        int tabStart=mid-(tabWidth*2);
        for(int i=0;i<4;i++){
            int tx=tabStart+i*tabWidth;
            d.fill(tx,y-1,tx+tabWidth-3,y+9,i==tab?0xFF4B245F:0xFF302038);
            d.drawCenteredTextWithShadow(c.textRenderer,labels[i],tx+(tabWidth-3)/2,y+1,i==tab?0xFFFFEAC8:0xFFD7C4A4);
        }

        int pageInset=28, lx=x+pageInset, rx=mid+pageInset, ty=y+36;
        int leftWidth=mid-x-2*pageInset-14, rightWidth=x+w-mid-2*pageInset-14;
        int ink=0xFF302015, accent=0xFF572B70, muted=0xFF76583A;
        if(tab==0){
            line(d,c,"SPELL INDEX",lx,ty,muted);
            line(d,c,"SPELL DETAILS",rx,ty,muted);
            if(spells().isEmpty()){
                wrapped(d,c,"This grimoire has no spells yet. Engrave a spell page at the Magic Writer, then add it from the Pages tab.",lx,ty+22,leftWidth,ink,5);
                line(d,c,"EMPTY PAGE",rx+8,ty+28,muted);
                wrapped(d,c,"Your selected spell's description, preview and casting information will appear here.",rx+8,ty+48,rightWidth-16,ink,5);
            } else {
                int visible=Math.min(7,Math.max(1,(h-83)/16));
                int start=Math.max(0,Math.min(selectedSpell-visible/2,spells().size()-visible));
                for(int i=start;i<Math.min(start+visible,spells().size());i++){
                    String n=spells().get(i).getAsJsonObject().get("name").getAsString();
                    line(d,c,(i==selectedSpell?"◆ ":"  ")+n,lx,ty+19+(i-start)*16,i==selectedSpell?accent:ink);
                }
                JsonObject s=spells().get(selectedSpell).getAsJsonObject();
                String name=s.get("name").getAsString();
                d.fill(rx+5,ty+20,rx+rightWidth-5,ty+24,0xFFB89A70);
                line(d,c,name,rx+8,ty+31,accent);
                JsonArray a=s.has("components")?s.getAsJsonArray("components"):new JsonArray();
                line(d,c,"Components: "+a.size(),rx+8,ty+48,ink);
                line(d,c,"Arcana cost: "+(10+a.size()*5),rx+8,ty+62,ink);
                String behavior=s.has("behavior")?s.get("behavior").getAsString():"No description available.";
                wrapped(d,c,behavior,rx+8,ty+79,rightWidth-16,ink,Math.max(2,(h-125)/11));
                line(d,c,removeMode?"ENTER: tear out page":"ENTER: cast selected spell",rx+8,y+h-25,accent);
            }
            
        } else if(tab==1){
            line(d,c,"UNBOUND SPELL PAGES",lx,ty,muted);
            line(d,c,"PAGE PREVIEW",rx,ty,muted);
            if(pages().isEmpty()){
                wrapped(d,c,"No loose Spell Pages are in your inventory. Engrave one at the Magic Writer first.",lx,ty+23,leftWidth,ink,5);
                line(d,c,"NO PAGE SELECTED",rx+8,ty+28,muted);
            } else {
                int visible=Math.min(8,Math.max(1,(h-83)/16));
                int start=Math.max(0,Math.min(selectedLoosePage-visible/2,pages().size()-visible));
                for(int i=start;i<Math.min(start+visible,pages().size());i++){
                    String n=pages().get(i).getAsString();
                    line(d,c,(i==selectedLoosePage?"◆ ":"  ")+n,lx,ty+19+(i-start)*16,i==selectedLoosePage?accent:ink);
                }
                String n=pages().get(selectedLoosePage).getAsString();
                line(d,c,n,rx+8,ty+32,accent);
                wrapped(d,c,"This loose page can be inserted into your current grimoire if it has room.",rx+8,ty+53,rightWidth-16,ink,4);
                line(d,c,"ENTER: insert selected page",rx+8,y+h-25,accent);
            }
            
        } else if(tab==2){
            int v=state.has("arcana")?state.get("arcana").getAsInt():0,m=state.has("maxArcana")?state.get("maxArcana").getAsInt():100;
            line(d,c,"ARCANA RESERVE",lx,ty,muted);
            line(d,c,"REGENERATION",rx,ty,muted);
            d.fill(lx,ty+28,lx+leftWidth-8,ty+43,0xFFB9A88C);
            d.fill(lx+2,ty+30,lx+2+(leftWidth-12)*v/Math.max(1,m),ty+41,0xFF75409A);
            line(d,c,v+" / "+m+" ARCANA",lx,ty+54,ink);
            wrapped(d,c,"Casting a spell consumes Arcana. The cost is based on the number of components in that spell.",rx+8,ty+27,rightWidth-16,ink,6);
            
        } else {
            line(d,c,"YOUR GRIMOIRE",lx,ty,muted);
            line(d,c,"CAPABILITIES",rx,ty,muted);
            line(d,c,"Stored pages: "+spells().size()+" / "+(state.has("slots")?state.get("slots").getAsInt():0),lx,ty+25,ink);
            line(d,c,"Max components: "+(state.has("maxComponents")?state.get("maxComponents").getAsInt():0),lx,ty+43,ink);
            wrapped(d,c,"The grimoire's tier determines how many spell pages it can hold and how complex each spell can be.",rx+8,ty+25,rightWidth-16,ink,6);
            line(d,c,"↑ / ↓  CHANGE TAB",lx,y+h-25,muted);
        }
        if(System.currentTimeMillis()<noticeUntil&&!notice.isBlank())d.drawCenteredTextWithShadow(c.textRenderer,notice,sw/2,y-13,0xFFFFFFFF);
        // Controls live on the parchment instead of covering the hotbar or crosshair.
        line(d,c,"↑ / ↓  TABS",x+12,y+h-10,0xFF5B402B);
        d.drawCenteredTextWithShadow(c.textRenderer,"ENTER  USE     X  REMOVE MODE",mid,y+h-10,0xFF5B402B);
    }
}