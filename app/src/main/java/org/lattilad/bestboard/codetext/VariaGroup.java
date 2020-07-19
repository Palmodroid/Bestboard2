package org.lattilad.bestboard.codetext;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Beothe on 2016.08.20..
 */
public class VariaGroup
    {
    // Should group contain its own code or not?
    private String code;
    private Varia varia;
    private List<VariaLegend> legends = new ArrayList<>();

    public VariaGroup(String code, List<VariaLegend> legends)
        {
        this.code = code;
        this.legends = legends;
        }

    public void setVaria(Varia varia )
        {
        this.varia = varia;
        }
        
    public Varia getVaria()
        {
        return varia;
        }

    public String getCode()
        {
        return code;
        }

    public void activate()
        {
        varia.setActiveGroup( this );
        }

    public String getText( int index )
        {
        if (index >= 0 && index < legends.size())
            return legends.get(index).getText();
        return "";
        }

    public String getTitle( int index )
        {
        if (index >= 0 && index < legends.size())
            return legends.get(index).getTitle();
        return "";
        }
    }
