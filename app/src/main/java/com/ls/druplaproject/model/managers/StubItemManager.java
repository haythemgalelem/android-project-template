package com.ls.druplaproject.model.managers;

import com.ls.drupal.DrupalClient;
import com.ls.druplaproject.model.data.dao.StubItemDAO;
import com.ls.druplaproject.model.data.vo.StubItemVO;
import com.ls.druplaproject.model.responses.StubItemResponse;

import android.os.Bundle;

import java.util.ArrayList;
import java.util.List;

/**
 * Created on 22.05.2015.
 */
public class StubItemManager extends SynchrondizedDatabaseManager<List<StubItemVO>,StubItemResponse,Bundle,String>{

    private final static String PAGE_ID_KEY = "page_ID";
    private final static String TAG_PREFIX = "stub_item_id:";
    private StubItemDAO dao ;

    public StubItemManager(DrupalClient client) {
        super(client);
        dao = new StubItemDAO();
    }

    /**
     * @param id
     * @return tag, used to identify request
     */
    public Object pullPage(String id)
    {
        return fetchData(getBundleForId(id));
    }

    @Override
    protected StubItemResponse getEntityToFetch(DrupalClient client, Bundle requestParams) {
        String requestId = getIdFromBundle(requestParams);
        StubItemResponse response =  new StubItemResponse(client);
        response.setPageId(requestId);
        return response;
    }

    @Override
    protected String getEntityRequestTag(Bundle params) {
        return getTagFromBundle(params);
    }

    @Override
    protected List<StubItemVO> readResponseFromRequest(StubItemResponse request, String tag) {
        if(request != null && !request.isEmpty()) {
            for(StubItemVO item:request)
            {
                item.setPageId(request.getPageId());
            }
            return new ArrayList<>(request);
        }else{
            return null;
        }
    }

    @Override
    protected boolean synchronizedStoreResponse(List<StubItemVO> response, String tag) {
        List<StubItemVO> items = new ArrayList<>(response);
        dao.saveData(items);
        return true;
    }

    @Override
    protected List<StubItemVO> synchronizeddRetoreResponse(String tag) {
        String requestId = getReqeustIdFromTag(tag);
        return  dao.readItemsForPageId(requestId);
    }

    private Bundle getBundleForId(String pageId)
    {
        Bundle result = new Bundle();
        result.putString(PAGE_ID_KEY, pageId);
        return result;
    }

    private String getIdFromBundle(Bundle bundle)
    {
        return bundle.getString(PAGE_ID_KEY);
    }

    private String getTagFromBundle(Bundle bundle)
    {
        return TAG_PREFIX + getIdFromBundle(bundle);
    }

    private String getReqeustIdFromTag(String tag)
    {
        return tag.substring(TAG_PREFIX.length());
    }
}