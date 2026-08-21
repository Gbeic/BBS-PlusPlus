package gbeic.bbsplusplus.forms.values;

import mchorse.bbs_mod.settings.values.core.ValueList;
import mchorse.bbs_mod.settings.values.mc.ValueItemStack;

public class ValueItemStacks extends ValueList<ValueItemStack>
{
    public ValueItemStacks(String id)
    {
        super(id);
    }

    @Override
    protected ValueItemStack create(String id)
    {
        return new ValueItemStack(id);
    }
}
