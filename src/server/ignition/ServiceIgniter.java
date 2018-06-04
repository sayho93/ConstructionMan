package server.ignition;

import configs.Constants;
import databases.paginator.ListBox;
import delayed.managers.PushManager;
import server.cafe24.Cafe24SMS;
import server.cafe24.Cafe24SMSManager;
import server.comm.DataMap;
import server.comm.RestProcessor;
import server.response.Response;
import server.response.ResponseConst;
import server.rest.DataMapUtil;
import server.rest.RestConstant;
import server.rest.RestUtil;
import server.temporaries.SMSAuth;
import services.AdminSVC;
import services.CommonSVC;
import services.UserSVC;
import spark.Service;
import utils.FileUploader;
import utils.Log;
import utils.MailSender;

import javax.servlet.MultipartConfigElement;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Calendar;
import java.util.List;

/**
 * @author 함의진
 * @version 2.0.0
 * 서버 실행을 위한 이그니션 클래스
 * @description (version 2.5.0) Response Transformer refactored with the lambda exp. and BaseIgniter applied
 * Jul-21-2017
 */
public class ServiceIgniter extends BaseIgniter{

    private Service service;

    private CommonSVC commonSVC;
    private UserSVC userSVC;
    private AdminSVC adminSVC;

    /**
     * 서버 실행에 필요한 전처리 작업을 위한 init 파트
     * utils 패키지가 포함하는 유틸리티 싱글턴의 경우, 이곳에서 상수로서 값을 전달하고, 존재하거나 초기화되었을 경우에 한해 인스턴스를 반환하도록
     * 별도로 인스턴스 취득자를 구성하였다.
     */
    {
        Cafe24SMS inst = Cafe24SMS.getInstance(
                "huneps71",
                "e6ac61f053b7abf60ee934857d2955c7",
                "070",
                "8804",
                "5688");
        Cafe24SMSManager.initialize(inst);

        commonSVC = new CommonSVC();
        userSVC = new UserSVC();

        try {
            MailSender.start("euijin.ham@richware.co.kr", "gpswpf12!", 20);
            PushManager.start("AAAALAuy9Ms:APA91bHvU-eINQYL59NviY_imyPrhNc76o_Kgb1J9GFv6LhYBl545-yfpHK6iShVUCsOrXNNcZdPznFzR4p5NBrFOnubcWD93DzxzyNG0yv3j5jNGg_X1fjT_jNYmTq8Bcr_IVv6fp3A");
            Cafe24SMSManager.getInstanceIfExisting().start(100);
            SMSAuth.getInstance().consume(5, 3);
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public static ServiceIgniter instance;

    public static ServiceIgniter getInstance() {
        if (instance == null) instance = new ServiceIgniter();
        return instance;
    }

    public void igniteServiceServer() {

        setProjectName("const_inn");
        setDeveloper("EuiJin.Ham");
        setCallSample("http://192.168.0.38:10040");
        setDebugMode(true);

        service = Service.ignite().port(RestConstant.REST_SERVICE);
        final File img_path = activateExternalDirectory(service, Constants.FILE_CONST.UPLOAD_DIR);

        service.before((req, res) -> {
            DataMap map = RestProcessor.makeProcessData(req.raw());
            Log.e("Connection", "Service Server [" + Calendar.getInstance().getTime().toString() + "] :: [" + req.pathInfo() + "] FROM [" + RestUtil.extractIp(req.raw()) + "] :: " + map);
            res.type(RestConstant.RESPONSE_TYPE_JSON);
        });

        super.get(service, "/system", (req, res) -> new Response(ResponseConst.CODE_SUCCESS, ResponseConst.MSG_SUCCESS, System.getenv()), "서버 시스템 환경을 확인하기 위한 API 입니다.");

        super.get(service, "/smstest", (req, res) -> {
            Cafe24SMSManager.getInstanceIfExisting().send("010-2948-4648", "test");
            return new Response(0, "", "");
        });

        super.post(service, "/imgUpload", (req, res) -> {
            try {
                final Path tempFile = Files.createTempFile(img_path.toPath(), "", ".jpg");
                req.attribute("org.eclipse.jetty.multipartConfig", new MultipartConfigElement("/temp"));
                try (InputStream input = req.raw().getPart("uploadImg").getInputStream()) {
                    Files.copy(input, tempFile, StandardCopyOption.REPLACE_EXISTING);
                }
                return new Response(ResponseConst.CODE_SUCCESS, ResponseConst.MSG_SUCCESS, tempFile.toString());
            }catch (Exception e){
                return new Response(ResponseConst.CODE_FAILURE, ResponseConst.MSG_FAILURE);
            }
        }, "이미지 업로드를 위한 API 입니다.", "uploadImg(multipart)");

        super.get(service, "/info/region", (req, res) -> {
            List<DataMap> retVal = commonSVC.getSidoList();
            return new Response(ResponseConst.CODE_SUCCESS, ResponseConst.MSG_SUCCESS, retVal);
        }, "시/도 목록을 취득하기 위한 API입니다.");

        super.get(service, "/info/region/:sidoID", (req, res) -> {
            final int sidoID = Integer.parseInt(req.params(":sidoID"));
            List<DataMap> retVal = commonSVC.getGugunList(sidoID);
            return new Response(ResponseConst.CODE_SUCCESS, ResponseConst.MSG_SUCCESS, retVal);
        }, "시/군/구 목록을 취득하기 위한 API입니다.", "sidoID[REST]");


        //TODO
        super.get(service, "/info/test/:sidoID", (req, res) -> {
            final int sidoID = Integer.parseInt(req.params(":sidoID"));
            List<DataMap> retVal = commonSVC.test2(sidoID);
            return new Response(ResponseConst.CODE_SUCCESS, ResponseConst.MSG_SUCCESS, retVal);
        }, "시/군/구 목록을 취득하기 위한 API입니다.", "sidoID[REST]");




        super.post(service, "/web/user/push/on/:id", (req, res) -> {
            final int id = Integer.parseInt(req.params(":id"));
            DataMap map = userSVC.turnOnPush(id);
            if(map == null) return new Response(ResponseConst.CODE_FAILURE, ResponseConst.MSG_FAILURE);
            return new Response(ResponseConst.CODE_SUCCESS, ResponseConst.MSG_SUCCESS, map);
        }, "사용사 설정 - 푸시 수신 여부(수신)를 설정하기 위한 API", "id[REST]");

        super.post(service, "/web/user/push/off/:id", (req, res) -> {
            final int id = Integer.parseInt(req.params(":id"));
            DataMap map = userSVC.turnOffPush(id);
            if(map == null) return new Response(ResponseConst.CODE_FAILURE, ResponseConst.MSG_FAILURE);
            return new Response(ResponseConst.CODE_SUCCESS, ResponseConst.MSG_SUCCESS, map);
        }, "사용사 설정 - 푸시 수신 여부(미수신)를 설정하기 위한 API", "id[REST]");

        super.get(service, "/web/user/auth/:phone", (req, res) -> {
            final String phone = req.params(":phone");

            if(phone == null || phone.trim().equals("")) return new Response(ResponseConst.CODE_INVALID_PARAM, ResponseConst.MSG_INVALID_PARAM);
            userSVC.userSMSAuth(phone);
            return new Response(ResponseConst.CODE_SUCCESS, ResponseConst.MSG_SUCCESS);
        }, "SMS 인증문자 발송을 위한 API", "phone[REST]");

        super.get(service, "/web/user/verify/:phone", (req, res) -> {
            DataMap map = RestProcessor.makeProcessData(req.raw());
            final String phone = req.params(":phone");

            boolean isValid = SMSAuth.getInstance().isValid(phone, map.getString("code"), 3);
            SMSAuth.getInstance().removeAuth(phone);
            if(isValid) return new Response(ResponseConst.CODE_SUCCESS, ResponseConst.MSG_SUCCESS);
            else return new Response(ResponseConst.CODE_UNAUTHORIZED, ResponseConst.MSG_UNAUTHORIZED);
        }, "SMS 코드 인증을 위한 API", "phone[REST]", "code");

        super.post(service, "/web/user/join", (req, res) -> {
            DataMap map = RestProcessor.makeProcessData(req.raw());
            if(DataMapUtil.isValid(map, "name", "account", "password", "phone", "age", "type", "pushKey", "sex")){
                final DataMap userInfo = userSVC.joinUser(map);

                if(userInfo != null) return Response.success(userInfo);
                else return Response.failure();
            }else{
                return new Response(ResponseConst.CODE_INVALID_PARAM, ResponseConst.MSG_INVALID_PARAM);
            }
        }, "APP 회원가입을 위한 API", "name", "account", "password", "phone", "age", "sex", "type",
                "pushKey", "region[ARR]", "work[ARR]", "career[ARR]", "welderType", "gearInfo[json]");

        super.get(service, "/web/user/checkAccountDuplication/:account", (req, res) -> {
            final String account = req.params(":account");
            DataMap map = userSVC.checkAccount(account);

            if(map == null) return new Response(ResponseConst.CODE_SUCCESS, ResponseConst.MSG_SUCCESS);
            else return new Response(ResponseConst.CODE_ALREADY_EXIST, ResponseConst.MSG_ALREADY_EXIST);
        }, "회원가입시 아이디 중복 체크를 위한 API", "account[REST]");

        super.get(service, "/web/user/checkPhoneDuplication/:phone", (req, res) -> {
            final String phone = req.params(":phone");
            DataMap map = userSVC.checkPhone(phone);

            if(map == null) return new Response(ResponseConst.CODE_SUCCESS, ResponseConst.MSG_SUCCESS);
            else return new Response(ResponseConst.CODE_ALREADY_EXIST, ResponseConst.MSG_ALREADY_EXIST);
        }, "회원가입시 휴대폰번호 중복 체크를 위한 API", "phone[REST]");

        super.post(service, "/web/register/search/:id", (req, res) -> {
            final int userId = Integer.parseInt(req.params(":id"));
            DataMap map = RestProcessor.makeProcessData(req.raw());

            if(DataMapUtil.isValid(map, "sidoId", "gugunId", "startDate", "endDate")){
                final int retCode = userSVC.registerSearch(userId, map);

                if(retCode == ResponseConst.CODE_SUCCESS) return new Response(ResponseConst.CODE_SUCCESS, ResponseConst.MSG_SUCCESS);
                else return new Response(ResponseConst.CODE_FAILURE, ResponseConst.MSG_FAILURE);
            } else
                return new Response(ResponseConst.CODE_INVALID_PARAM, ResponseConst.MSG_INVALID_PARAM);
        },"인력찾기/장비찾기를 위한 API", "id[REST]", "type", "work[ARR]", "career[ARR]", "welderType", "sidoId",
                "gugunId", "name", "startDate", "endDate", "lodging", "price", "discussLater", "gearId", "attachment");

        super.post(service, "/web/user/login", (req, res) -> {
            DataMap map = RestProcessor.makeProcessData(req.raw());

            if(DataMapUtil.isValid(map, "account", "password")){
                DataMap userInfo = userSVC.userLogin(map);
                if(userInfo != null) return new Response(ResponseConst.CODE_SUCCESS, ResponseConst.MSG_SUCCESS, userInfo);
                else return new Response(ResponseConst.CODE_FAILURE, ResponseConst.MSG_FAILURE);
            } else{
                return new Response(ResponseConst.CODE_INVALID_PARAM, ResponseConst.MSG_INVALID_PARAM);
            }
        }, "유저 로그인을 위한 API", "account", "password");

        super.get(service, "web/user/basic/:id", (req, res) -> {
            final int id = Integer.parseInt(req.params(":id"));
            DataMap userInfo = userSVC.getUserBasic(id);
            return Response.success(userInfo);
        }, "유저 기본 정보 취득을 위한 API", "id[REST]");

        super.get(service, "/web/user/info/:id", (req, res) -> {
            final int id = Integer.parseInt(req.params(":id"));
            DataMap map = userSVC.getUserInfo(id);

            if(map == null) return new Response(ResponseConst.CODE_FAILURE, ResponseConst.MSG_FAILURE);
            else return new Response(ResponseConst.CODE_SUCCESS, ResponseConst.MSG_SUCCESS, map);
        }, "마이페이지 유저 장비/인력 정보 취득을 위한 API", "id[REST]");

        super.get(service, "/web/user/region/:id", (req, res) -> {
            final int id = Integer.parseInt(req.params(":id"));
            List<DataMap> list = userSVC.getUserRegion(id);
            if(list == null) return new Response(ResponseConst.CODE_FAILURE, ResponseConst.MSG_FAILURE);
            else return new Response(ResponseConst.CODE_SUCCESS, ResponseConst.MSG_SUCCESS, list);
        }, "유저 지역정보 취득 API", "id[REST]");

        super.get(service, "/web/user/update/pushKey/:id", (req, res) -> {
            final int id = Integer.parseInt(req.params(":id"));
            DataMap map = RestProcessor.makeProcessData(req.raw());
            final String pushKey = map.getString("pushKey");

            DataMap userInfo = userSVC.updatePushKey(id, pushKey);

            if(userInfo != null) return new Response(ResponseConst.CODE_SUCCESS, ResponseConst.MSG_SUCCESS, userInfo);
            else return new Response(ResponseConst.CODE_FAILURE, ResponseConst.MSG_FAILURE);
        }, "유저 푸시키 업데이트를 위한 API", "id[REST]", "pushKey");

        super.post(service, "/web/user/update/info/:id", (req, res) -> {
            final int id = Integer.parseInt(req.params(":id"));
            DataMap map = RestProcessor.makeProcessData(req.raw());

            if(DataMapUtil.isValid(map, "type", "region")){
                DataMap userInfo = userSVC.updateUserInfo(id, map);
                if(userInfo != null) return new Response(ResponseConst.CODE_SUCCESS, ResponseConst.MSG_SUCCESS, map);
                else return new Response(ResponseConst.CODE_FAILURE, ResponseConst.MSG_FAILURE);
            }else{
                return new Response(ResponseConst.CODE_INVALID_PARAM, ResponseConst.MSG_INVALID_PARAM);
            }

        }, "마이페이지 유저 정보 변경을 위한 API", "id[REST]", "type", "region[ARR]", "work[ARR]",
                "career[ARR]", "welderType", "gearId", "attachment");

        super.get(service, "web/user/apply/:id", (req, res) -> {
            final int userId = Integer.parseInt(req.params(":id"));
            DataMap map = RestProcessor.makeProcessData(req.raw());

            if(DataMapUtil.isValid(map, "searchId")){
                userSVC.applySearch(userId, map.getInt("searchId"));
                return new Response(ResponseConst.CODE_SUCCESS, ResponseConst.MSG_SUCCESS);
            }else{
                return new Response(ResponseConst.CODE_INVALID_PARAM, ResponseConst.MSG_INVALID_PARAM);
            }
        }, "공고 지원을 위한 API", "id[REST]", "searchId");

        super.post(service, "/admin/login", (req, res) -> {
            DataMap map = RestProcessor.makeProcessData(req.raw());

            if(DataMapUtil.isValid(map, "account", "password")){
                DataMap adminInfo = adminSVC.adminLogin(map);
                if(adminInfo != null) return new Response(ResponseConst.CODE_SUCCESS, ResponseConst.MSG_SUCCESS, adminInfo);
                else return new Response(ResponseConst.CODE_FAILURE, ResponseConst.MSG_FAILURE);
            }else{
                return new Response(ResponseConst.CODE_INVALID_PARAM, ResponseConst.MSG_INVALID_PARAM);
            }
        }, "관리자 로그인을 위한 API", "account", "password");

        super.get(service, "/admin/userList", (req, res) -> {
            DataMap map = RestProcessor.makeProcessData(req.raw());
            final int page = map.getInt("page", 1);
            final int limit = map.getInt("limit", 20);
            final String account = map.getString("account", "");
            final String phone = map.getString("phone", "");

            ListBox retVal = adminSVC.getUserList(page, limit, account, phone);
            return new Response(ResponseConst.CODE_SUCCESS, ResponseConst.MSG_SUCCESS, retVal);
        }, "유저 목록 취득을 위한 API. 아이디와 전화번호를 통해 검색할 수 있음", "page", "limit", "account", "phone");

        super.get(service, "/web/introprocess", (req, res) -> {
            return new Response(ResponseConst.CODE_SUCCESS, ResponseConst.MSG_SUCCESS, null);
        });

        super.get(service, "/info/work", (req, res) -> {
            DataMap map = RestProcessor.makeProcessData(req.raw());
            final int[] workArr = map.getStringToIntArr("work", ",");
            List<DataMap> retVal = commonSVC.getWorkInfo(workArr);
            return new Response(ResponseConst.CODE_SUCCESS, ResponseConst.MSG_SUCCESS, retVal);
        }, "직종 번호를 받아 관련 정보를 반환하는 API", "work[ARR]");

        super.get(service, "/info/gearOption1", (req, res) -> {
            DataMap map = RestProcessor.makeProcessData(req.raw());
            final String name = map.getString("name");
            List<DataMap> retVal = commonSVC.getGearOption1(name);
            return new Response(ResponseConst.CODE_SUCCESS, ResponseConst.MSG_SUCCESS, retVal);
        }, "장비의 첫 번째 옵션 목록 취득을 위한 API", "name");

        super.get(service, "/info/gearOption2", (req, res) -> {
            DataMap map = RestProcessor.makeProcessData(req.raw());
            final String name = map.getString("name");
            final String detail = map.getString("detail");
            List<DataMap> retVal = commonSVC.getGearOption2(name, detail);
            return new Response(ResponseConst.CODE_SUCCESS, ResponseConst.MSG_SUCCESS, retVal);
        }, "장비의 두 번째 옵션 목록 취득을 위한 API", "name", "detail");

        super.get(service, "/info/gear/:id", (req, res) -> {
            final int gearId = Integer.parseInt(req.params(":id"));
            DataMap map = commonSVC.getGearInfo(gearId);
            if(map != null) return new Response(ResponseConst.CODE_SUCCESS, ResponseConst.MSG_SUCCESS, map);
            else return new Response(ResponseConst.CODE_FAILURE, ResponseConst.MSG_FAILURE);
        }, "장비 한 개의 정보 취득을 위한 API", "id[REST]");

        super.post(service, "/web/user/update/name/:id", (req, res) -> {
            final int id = Integer.parseInt(req.params(":id"));
            DataMap map = RestProcessor.makeProcessData(req.raw());
            final String name = map.getString("name");
            DataMap userInfo = userSVC.updateUserName(id, name);
            return new Response(ResponseConst.CODE_SUCCESS, ResponseConst.MSG_SUCCESS, userInfo);

        }, "유저 이름 변경을 위한 API", "id[REST]", "name");

        super.post(service, "/web/user/withdraw/:id", (req, res) -> {
            final int id = Integer.parseInt(req.params(":id"));
            DataMap userInfo = userSVC.withdrawUser(id);
            final int status = userInfo.getInt("status");
            if(status == 0) return new Response(ResponseConst.CODE_SUCCESS, ResponseConst.MSG_SUCCESS, null);
            else return new Response(ResponseConst.CODE_FAILURE, ResponseConst.MSG_FAILURE);
        }, "회원 탈퇴를 위한 API", "id[REST]");

        super.get(service, "/web/user/findID", (req, res) -> {
            DataMap map = RestProcessor.makeProcessData(req.raw());
            DataMap userInfo = userSVC.getUserByNamePhone(map);
            if(userInfo != null) return new Response(ResponseConst.CODE_SUCCESS, ResponseConst.MSG_SUCCESS, userInfo);
            else return new Response(ResponseConst.CODE_FAILURE, ResponseConst.MSG_FAILURE);
        }, "회원 ID 취득을 위한 API", "name", "phone");

        super.get(service, "/web/user/findPW", (req, res) -> {
            DataMap map = RestProcessor.makeProcessData(req.raw());
            DataMap userInfo = userSVC.getUSerByAccountPhone(map);
            if(userInfo != null) return new Response(ResponseConst.CODE_SUCCESS, ResponseConst.MSG_SUCCESS, userInfo);
            else return new Response(ResponseConst.CODE_FAILURE, ResponseConst.MSG_FAILURE);
        }, "비밀번호 찾기 조건에 맞는 회원 정보를 취득하기 위한 API", "name", "phone", "account");

        super.post(service, "/web/user/changePW/:id", (req, res) -> {
            final int id = Integer.parseInt(req.params(":id"));
            DataMap map = RestProcessor.makeProcessData(req.raw());
            final String password = map.getString("password");

            userSVC.changePassword(id, password);
            return new Response(ResponseConst.CODE_SUCCESS, ResponseConst.MSG_SUCCESS);
        }, "비밀번호 변경을 위한 API", "id[REST], password");

        super.post(service, "/web/user/updateImg/:id", (req, res) -> {
            final int id = Integer.parseInt(req.params(":id"));
            DataMap map = RestProcessor.makeProcessData(req.raw());
            final String imgPath = map.getString("imgPath");

            DataMap userInfo = userSVC.updateUserImg(id, imgPath);
            if(userInfo != null) return new Response(ResponseConst.CODE_SUCCESS, ResponseConst.MSG_SUCCESS, userInfo);
            return new Response(ResponseConst.CODE_FAILURE, ResponseConst.MSG_FAILURE);
        }, "유저 프로필 사진 업데이트를 위한 API", "id[REST]", "imgPath");

        super.get(service, "/web/user/point/:id", (req, res) -> {
            final int id = Integer.parseInt(req.params(":id"));
            int point = userSVC.getUserPoint(id);
            return new Response(ResponseConst.CODE_SUCCESS, ResponseConst.MSG_SUCCESS, point);
        }, "유저 포인트 조회를 위한 API", "id[REST]");

        super.post(service, "/web/user/point/inc/:id", (req, res) -> {
            DataMap map = RestProcessor.makeProcessData(req.raw());
            final int id = Integer.parseInt(req.params(":id"));
            if(DataMapUtil.isValid(map, "inc", "payType")){
                final int inc = map.getInt("inc");
                final int payType = map.getInt("payType");
                final String comment = map.getString("comment");
                int point = userSVC.getUserPoint(id);
                if(point + inc < 0){
                    return new Response(ResponseConst.CODE_FAILURE, ResponseConst.MSG_ILLEGAL_STATE);
                }else{
                    int patched;
                    if(payType == 0) {
                        patched = userSVC.changeUserPoint(id, inc, payType, -1, comment);
                    } else {
                        final int paymentId = map.getInt("paymentId");
                        patched = userSVC.changeUserPoint(id, inc, payType, paymentId, comment);
                    }
                    return new Response(ResponseConst.CODE_SUCCESS, ResponseConst.MSG_SUCCESS, patched);
                }
            }else{
                return new Response(ResponseConst.CODE_INVALID_PARAM, ResponseConst.MSG_INVALID_PARAM);
            }
        }, "포인트 수치 변경을 위한 API", "id[REST]", "inc", "payType", "comment[optional]");

        super.get(service, "/web/user/pointList/:id", (req, res) -> {
            final int id = Integer.parseInt(req.params(":id"));
            List<DataMap> pointList = userSVC.getPointList(id);
            return Response.success(pointList);
        }, "유저 포인트 히스토리 조회를 위한 API", "id[REST]");

        super.get(service, "/web/user/myApplyList/:id", (req, res) -> {
            final int id = Integer.parseInt(req.params(":id"));
            List<DataMap> applyList = userSVC.getApplyList(id);
            return Response.success(applyList);
        }, "유저가 지원한 리스트 조회를 위한 API", "id[REST]");

        super.get(service, "/web/user/paid/:id", (req, res) -> {
            final int id = Integer.parseInt(req.params(":id"));
            List<DataMap> applyList = userSVC.getPaidList(id);
            return Response.success(applyList);
        }, "결제한 공고 정보 리스트 조회를 위한 API", "id[REST]");

        super.post(service, "/web/user/paid/del/:id", (req, res) -> {
            final int id = Integer.parseInt(req.params(":id"));
            userSVC.hidePaidItem(id);
            return Response.success(null);
        });

        super.post(service, "/web/user/apply/del/:id", (req, res) -> {
            final int id = Integer.parseInt(req.params(":id"));
            userSVC.hideApplyHistory(id);
            return Response.success(null);
        });

        super.post(service, "/web/user/point/del/:id", (req, res) -> {
            final int id = Integer.parseInt(req.params(":id"));
            userSVC.hidePointHistory(id);
            return Response.success(null);
        });

        super.get(service, "/web/user/applications/:id", (req, res) -> {
            final int id = Integer.parseInt(req.params(":id"));
            List<DataMap> applicationList = userSVC.getApps(id);
            if(applicationList == null) return new Response(ResponseConst.CODE_NO_PROPER_VALUE, ResponseConst.MSG_NO_PROPER_VALUE);
            return Response.success(applicationList);
        }, "구인 리스트 취득을 위한 API", "id[REST]");

        super.post(service, "/web/user/point/use/:id", (req, res) -> {
            final int id = Integer.parseInt(req.params(":id"));
            int retVal = userSVC.usePoint(id);
            if(retVal == -1) return new Response(ResponseConst.CODE_NO_PROPER_VALUE, ResponseConst.MSG_NO_PROPER_VALUE);
            else if(retVal == -2) return new Response(ResponseConst.CODE_NOT_EXISTING, ResponseConst.MSG_NOT_EXISTING);
            return Response.success(null);
        }, "포인트 소비를 위한 API", "id[REST]");

    }

}
