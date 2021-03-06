/*
 * Copyright 2017 Huawei Technologies Co., Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.openo.sdno.localsiteservice.impl.site;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.openo.baseservice.remoteservice.exception.ServiceException;
import org.openo.sdno.framework.container.util.JsonUtil;
import org.openo.sdno.framework.container.util.UuidUtils;
import org.openo.sdno.localsiteservice.dao.BaseResourceDao;
import org.openo.sdno.localsiteservice.dao.ModelDataDao;
import org.openo.sdno.localsiteservice.dao.SiteModelDao;
import org.openo.sdno.localsiteservice.inf.site.InternetGatewayService;
import org.openo.sdno.localsiteservice.model.vpn.InternalConnectionModel;
import org.openo.sdno.localsiteservice.sbi.site.InternalConnectionSbiService;
import org.openo.sdno.localsiteservice.sbi.site.SNatSbiService;
import org.openo.sdno.localsiteservice.sbi.site.TemplateSbiService;
import org.openo.sdno.overlayvpn.brs.model.NetworkElementMO;
import org.openo.sdno.overlayvpn.dao.common.InventoryDao;
import org.openo.sdno.overlayvpn.inventory.sdk.util.InventoryDaoUtil;
import org.openo.sdno.overlayvpn.model.common.enums.ActionStatus;
import org.openo.sdno.overlayvpn.model.v2.cpe.CpeRoleType;
import org.openo.sdno.overlayvpn.model.v2.internetgateway.NbiInternetGatewayModel;
import org.openo.sdno.overlayvpn.model.v2.internetgateway.SbiSnatNetModel;
import org.openo.sdno.overlayvpn.model.v2.result.ComplexResult;
import org.openo.sdno.overlayvpn.model.v2.site.NbiSiteModel;
import org.openo.sdno.overlayvpn.result.ResultRsp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Implementation class of InternetGateway Service.<br>
 * 
 * @author
 * @version SDNO 0.5 2017-1-19
 */
@Service
public class InternetGatewayServiceImpl implements InternetGatewayService {

    private static final Logger LOGGER = LoggerFactory.getLogger(InternetGatewayServiceImpl.class);

    private static final int MIN_ACL_NUMBER = 3000;

    private static final int MAX_ACL_NUMBER = 3999;

    @Autowired
    private SNatSbiService sbiService;

    @Autowired
    private InternalConnectionSbiService internalConnectionSbiService;

    @Autowired
    private TemplateSbiService templateSbiSrevice;

    @Autowired
    private SiteModelDao siteModelDao;

    @Autowired
    private BaseResourceDao baseResourceDao;

    @Autowired
    private TemplateSbiService templateSbiService;

    @Override
    public NbiInternetGatewayModel query(HttpServletRequest request, String internetGatewayId) throws ServiceException {
        InventoryDao<NbiInternetGatewayModel> internetGatewayDao =
                new InventoryDaoUtil<NbiInternetGatewayModel>().getInventoryDao();
        ResultRsp<NbiInternetGatewayModel> internetGatewayResultRsp =
                internetGatewayDao.query(NbiInternetGatewayModel.class, internetGatewayId, null);
        if(!internetGatewayResultRsp.isSuccess()) {
            LOGGER.error("Query internet gateway model failed");
            throw new ServiceException("Query internet gateway model failed");
        }

        NbiInternetGatewayModel internetGatewayModel = internetGatewayResultRsp.getData();
        fillQueryInternetGatewayModel(internetGatewayModel);

        return internetGatewayModel;
    }

    @Override
    public ComplexResult<NbiInternetGatewayModel> batchQuery(String name, String tenantId, String siteId,
            String pageNum, String pageSize) throws ServiceException {

        Map<String, Object> filterMap = new HashMap<>();

        if(StringUtils.isNotBlank(name)) {
            filterMap.put("name", Arrays.asList(name));
        }

        if(StringUtils.isNotBlank(tenantId)) {
            filterMap.put("tenantId", Arrays.asList(tenantId));
        }

        if(StringUtils.isNotBlank(siteId)) {
            filterMap.put("siteId", Arrays.asList(siteId));
        }

        InventoryDao<NbiInternetGatewayModel> internetGatewayDao =
                new InventoryDaoUtil<NbiInternetGatewayModel>().getInventoryDao();
        ResultRsp<List<NbiInternetGatewayModel>> queryResultRsp =
                internetGatewayDao.queryByFilter(NbiInternetGatewayModel.class, JsonUtil.toJson(filterMap), null);

        List<NbiInternetGatewayModel> internetGatewayModelList = queryResultRsp.getData();
        if(null == internetGatewayModelList) {
            LOGGER.error("NbiInternetGatewayModel query failed");
            throw new ServiceException("NbiInternetGatewayModel query failed");
        }

        for(NbiInternetGatewayModel internetGatewayModel : internetGatewayModelList) {
            fillQueryInternetGatewayModel(internetGatewayModel);
        }

        ComplexResult<NbiInternetGatewayModel> complexResult = new ComplexResult<>();
        complexResult.setData(internetGatewayModelList);
        complexResult.setTotal(internetGatewayModelList.size());

        return complexResult;
    }

    @Override
    public ResultRsp<NbiInternetGatewayModel> create(HttpServletRequest request,
            NbiInternetGatewayModel internetGatewayModel) throws ServiceException {

        // Fill Site and NetworkElement Info
        fillNeAndSite(internetGatewayModel);

        internetGatewayModel.allocateUuid();
        internetGatewayModel.setVpnId(UuidUtils.createUuid());
        internetGatewayModel.setActionState(ActionStatus.CREATING.getName());

        InventoryDao<NbiInternetGatewayModel> internetGatewayDao =
                new InventoryDaoUtil<NbiInternetGatewayModel>().getInventoryDao();
        ResultRsp<NbiInternetGatewayModel> insertResultRsp = internetGatewayDao.insert(internetGatewayModel);
        if(!insertResultRsp.isValid()) {
            LOGGER.error("NbiInternetGatewayModel insert failed");
            throw new ServiceException("NbiInternetGatewayModel insert failed");
        }

        List<SbiSnatNetModel> sNatNetModels = buildSNatNetModels(internetGatewayModel);

        InventoryDao<SbiSnatNetModel> sNatNetModelDao = new InventoryDaoUtil<SbiSnatNetModel>().getInventoryDao();
        // Send to Driver
        for(SbiSnatNetModel sNatNetModel : sNatNetModels) {
            ResultRsp<SbiSnatNetModel> createResultRsp = sbiService.create(sNatNetModel);
            if(!createResultRsp.isValid()) {
                LOGGER.error("SbiSnatNetModel create in driver failed");
                throw new ServiceException("SbiSnatNetModel create in driver failed");
            }
            ResultRsp<SbiSnatNetModel> insertNetModelResultRsp = sNatNetModelDao.insert(createResultRsp.getData());
            if(!insertNetModelResultRsp.isValid()) {
                LOGGER.error("SbiSnatNetModel insert in database failed");
                throw new ServiceException("SbiSnatNetModel insert in database failed");
            }
        }

        // Create Internal Connection
        InternalConnectionModel internalConnectionModel = buildInternalConnectionModel(internetGatewayModel);

        ResultRsp<InternalConnectionModel> createConnectionResultRsp =
                internalConnectionSbiService.create(internalConnectionModel);
        if(!createConnectionResultRsp.isSuccess()) {
            LOGGER.error("Internal connection create failed");
            throw new ServiceException("Internal connection create failed");
        }

        internetGatewayModel.setActionState(ActionStatus.NORMAL.getName());
        return internetGatewayDao.update(internetGatewayModel, "actionState");
    }

    @Override
    public void delete(HttpServletRequest request, NbiInternetGatewayModel internetGatewayModel)
            throws ServiceException {
        InventoryDao<NbiInternetGatewayModel> internetGatewayDao =
                new InventoryDaoUtil<NbiInternetGatewayModel>().getInventoryDao();

        internetGatewayModel.setActionState(ActionStatus.DELETING.getName());
        ResultRsp<NbiInternetGatewayModel> updateStatusResultRsp =
                internetGatewayDao.update(internetGatewayModel, "actionState");
        if(!updateStatusResultRsp.isSuccess()) {
            LOGGER.error("NbiInternetGatewayModel status update failed");
            throw new ServiceException("NbiInternetGatewayModel status update failed");
        }

        List<SbiSnatNetModel> sNatNetModels = querySNatModelByGatewayId(internetGatewayModel.getUuid());

        deleteSbiSnatNetModels(sNatNetModels);

        // Delete Internal Connection
        ResultRsp<InternalConnectionModel> deleteConnectionResultRsp =
                internalConnectionSbiService.delete(internetGatewayModel.getVpnId());
        if(!deleteConnectionResultRsp.isSuccess()) {
            LOGGER.error("InternalConnectionModel delete failed");
            throw new ServiceException("InternalConnectionModel delete failed");
        }

        // Delete from database
        ResultRsp<String> deleteGatewayResultRsp =
                internetGatewayDao.delete(NbiInternetGatewayModel.class, internetGatewayModel.getUuid());
        if(!deleteGatewayResultRsp.isSuccess()) {
            LOGGER.error("NbiInternetGatewayModel delete failed");
            throw new ServiceException("NbiInternetGatewayModel delete failed");
        }
    }

    @Override
    public ResultRsp<NbiInternetGatewayModel> update(HttpServletRequest request,
            NbiInternetGatewayModel internetGatewayModel) throws ServiceException {
        InventoryDao<NbiInternetGatewayModel> internetGatewayDao =
                new InventoryDaoUtil<NbiInternetGatewayModel>().getInventoryDao();

        return internetGatewayDao.update(internetGatewayModel, "name,description");
    }

    private void fillNeAndSite(NbiInternetGatewayModel internetGatewayModel) throws ServiceException {

        List<NetworkElementMO> siteNes = baseResourceDao.queryNeBySiteId(internetGatewayModel.getSiteId());
        if(CollectionUtils.isEmpty(siteNes)) {
            LOGGER.error("No cpe device in this site");
            throw new ServiceException("No cpe device in this site");
        }

        internetGatewayModel.setNes(siteNes);

        ResultRsp<NbiSiteModel> queryResultRsp =
                (new ModelDataDao<NbiSiteModel>()).query(NbiSiteModel.class, internetGatewayModel.getSiteId());
        if(!queryResultRsp.isValid()) {
            LOGGER.error("Site model query failed or does not exist");
            throw new ServiceException("Site model query failed or does not exist");
        }

        internetGatewayModel.setSite(queryResultRsp.getData());
    }

    private void fillQueryInternetGatewayModel(NbiInternetGatewayModel internetGatewayModel) throws ServiceException {
        if(null != internetGatewayModel.getGwNeId()) {
            return;
        }

        ResultRsp<NbiSiteModel> queryResultRsp =
                (new ModelDataDao<NbiSiteModel>()).query(NbiSiteModel.class, internetGatewayModel.getSiteId());
        if(!queryResultRsp.isValid()) {
            LOGGER.error("Site model query failed or does not exist");
            throw new ServiceException("Site model query failed or does not exist");
        }

        internetGatewayModel.setSite(queryResultRsp.getData());

        if("localFirst".equals(internetGatewayModel.getDeployPosition())) {
            fillNeAndWanNameInfo(internetGatewayModel, CpeRoleType.THIN_CPE);
        } else if("cloudFirst".equals(internetGatewayModel.getDeployPosition())) {
            fillNeAndWanNameInfo(internetGatewayModel, CpeRoleType.CLOUD_CPE);
        }
    }

    /**
     * Build SNatNetModels by InternetGateway Model.<br>
     * 
     * @param internetGatewayModel InternetGateway model
     * @return List of SbiSnatNetModel built
     * @throws ServiceException when build failed
     * @since SDNO 0.5
     */
    private List<SbiSnatNetModel> buildSNatNetModels(NbiInternetGatewayModel internetGatewayModel)
            throws ServiceException {
        List<SbiSnatNetModel> sNatNetModels = new ArrayList<>();
        List<String> sourceSubnets = internetGatewayModel.getSourceSubnets();
        if(CollectionUtils.isEmpty(sourceSubnets)) {
            sNatNetModels.add(buildSNatNetModel(internetGatewayModel, null));
        } else {
            for(String sourceSubnet : sourceSubnets) {
                sNatNetModels.add(buildSNatNetModel(internetGatewayModel, sourceSubnet));
            }
        }
        return sNatNetModels;
    }

    /**
     * Build SNatNetModel.<br>
     * 
     * @param internetGatewayModel NbiInternetGatewayModel Object
     * @param subnetId Subnet Uuid
     * @return SNatNetModel built
     * @throws ServiceException when build failed
     * @since SDNO 0.5
     */
    private SbiSnatNetModel buildSNatNetModel(NbiInternetGatewayModel internetGatewayModel, String subnetId)
            throws ServiceException {

        NetworkElementMO cloudCpeNe = siteModelDao.getSiteCloudCpe(internetGatewayModel.getSiteId());
        String templateName = siteModelDao.getTemplateName(internetGatewayModel.getSiteId());
        String wanInfName = templateSbiSrevice.getWanName(templateName);

        SbiSnatNetModel sNatNetModel = new SbiSnatNetModel();
        sNatNetModel.setUuid(UuidUtils.createUuid());
        sNatNetModel.setNeId(cloudCpeNe.getId());
        sNatNetModel.setDeviceId(cloudCpeNe.getNativeID());
        sNatNetModel.setControllerId(cloudCpeNe.getControllerID().get(0));

        sNatNetModel.setAclNumber(String.valueOf(generateRandomAclNumber()));
        sNatNetModel.setIfName(wanInfName);
        sNatNetModel.setInternetGatewayId(internetGatewayModel.getUuid());

        String infPublicIp = baseResourceDao.queryInterfaceByName(cloudCpeNe.getId(), wanInfName).getIpAddress();
        if(StringUtils.isEmpty(infPublicIp)) {
            LOGGER.error("Current wan interface has no Ip Address");
            throw new ServiceException("Current wan interface has no Ip Address");
        }

        sNatNetModel.setStartPublicIpAddress(infPublicIp + "/32");
        sNatNetModel.setName("snat_" + sNatNetModel.getUuid());
        sNatNetModel.setSubnetId(subnetId);

        sNatNetModel.setTenantId(internetGatewayModel.getTenantId());
        sNatNetModel.setDescription(internetGatewayModel.getDescription());
        sNatNetModel.setQosPreNat("true");

        sNatNetModel.setAclId(UuidUtils.createUuid());
        sNatNetModel.setNatId(UuidUtils.createUuid());

        return sNatNetModel;
    }

    /**
     * Build InternalConnection Model.<br>
     * 
     * @param internetGatewayModel NbiInternetGatewayModel data
     * @return InternalConnectionModel built
     * @throws ServiceException when build failed
     * @since SDNO 0.5
     */
    private InternalConnectionModel buildInternalConnectionModel(NbiInternetGatewayModel internetGatewayModel)
            throws ServiceException {

        InternalConnectionModel internalConnectionModel = new InternalConnectionModel();
        internalConnectionModel.setId(internetGatewayModel.getVpnId());
        internalConnectionModel.setName(internetGatewayModel.getName());
        internalConnectionModel.setDescription(internetGatewayModel.getDescription());
        internalConnectionModel.setTenantId(internetGatewayModel.getTenantId());
        internalConnectionModel.setDeployPosition(internetGatewayModel.getDeployPosition());
        internalConnectionModel.setDeployStatus("deploy");
        internalConnectionModel.setDestNeId(internetGatewayModel.getNes().get(0).getId());
        internalConnectionModel.setSrcNeId(internetGatewayModel.getNes().get(1).getId());

        internalConnectionModel.setVpnTemplateName(siteModelDao.getTemplateName(internetGatewayModel.getSiteId()));
        internalConnectionModel.setSiteId(internetGatewayModel.getSiteId());

        return internalConnectionModel;
    }

    /**
     * Query SNatNetModel by Gateway Id.<br>
     * 
     * @param internetGatewayId Internet Gateway Uuid
     * @return SNatNetModel queried out
     * @throws ServiceException when query failed
     * @since SDNO 0.5
     */
    private List<SbiSnatNetModel> querySNatModelByGatewayId(String internetGatewayId) throws ServiceException {

        InventoryDao<SbiSnatNetModel> sNatNetModelDao = new InventoryDaoUtil<SbiSnatNetModel>().getInventoryDao();

        Map<String, Object> filterMap = new HashMap<>();
        filterMap.put("internetGatewayId", Arrays.asList(internetGatewayId));

        ResultRsp<List<SbiSnatNetModel>> queryResultRsp =
                sNatNetModelDao.batchQuery(SbiSnatNetModel.class, JsonUtil.toJson(filterMap));

        return queryResultRsp.getData();
    }

    /**
     * Delete SnatNetModels.<br>
     * 
     * @param sNatNetModels SbiSnatNetModel need to delete
     * @throws ServiceException when delete failed
     * @since SDNO 0.5
     */
    private void deleteSbiSnatNetModels(List<SbiSnatNetModel> sNatNetModels) throws ServiceException {

        InventoryDao<SbiSnatNetModel> sNatNetModelDao = new InventoryDaoUtil<SbiSnatNetModel>().getInventoryDao();

        for(SbiSnatNetModel sNatNetModel : sNatNetModels) {
            // Send to driver
            ResultRsp<String> deleteResultRsp = sbiService.delete(sNatNetModel);
            if(!deleteResultRsp.isSuccess()) {
                LOGGER.error("SbiSnatNetModel delete from adapter failed, need to check driver service.");
                throw new ServiceException("SbiSnatNetModel delete from adapter failed");
            }
            // Delete from database
            ResultRsp<String> deleteNetModelResultRsp =
                    sNatNetModelDao.delete(SbiSnatNetModel.class, sNatNetModel.getUuid());
            if(!deleteNetModelResultRsp.isSuccess()) {
                LOGGER.error("SbiSnatNetModel delete failed");
                throw new ServiceException("SbiSnatNetModel delete failed");
            }
        }
    }

    private static int generateRandomAclNumber() {
        Random random = new Random();
        return random.nextInt(MAX_ACL_NUMBER - MIN_ACL_NUMBER + 1) + MIN_ACL_NUMBER;
    }

    private void fillNeAndWanNameInfo(NbiInternetGatewayModel internetGatewayModel, CpeRoleType cpeRoleType)
            throws ServiceException {
        NetworkElementMO cpeNeMO =
                baseResourceDao.queryCpeBySiteIdAndCpeType(internetGatewayModel.getSiteId(), cpeRoleType.getName());
        String templateName = siteModelDao.getTemplateName(internetGatewayModel.getSiteId());
        List<String> wanNames =
                templateSbiService.getWanNameList(templateName, cpeRoleType.getName(), cpeNeMO.getProductName());
        fillAdditionalInfo(internetGatewayModel, cpeNeMO, wanNames);
    }

    private void fillAdditionalInfo(NbiInternetGatewayModel internetGatewayModel, NetworkElementMO neMO,
            List<String> ifNames) {
        internetGatewayModel.setGwNeId(neMO.getId());
        internetGatewayModel.setWanNames(JsonUtil.toJson(ifNames));
    }

}
