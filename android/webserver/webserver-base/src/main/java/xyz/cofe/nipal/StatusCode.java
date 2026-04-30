package xyz.cofe.nipal;

public enum StatusCode {
    Continue(100),
    Switching_Protocols(101),
    Processing(102),

    Ok(200),
    Created(201),
    Accepted(202),
    Non_Authoritative_Information(203),
    No_Content(204),
    Reset_Content(205),
    Partial_Content(206),
    Multi_Status(207),

    Multiple_Choices(300),
    Moved_Permanently(301),
    Found(302),
    See_Other(303),
    Not_Modified(304),
    Use_Proxy(305),
    Temporary_Redirect(307),
    Permanent_Redirect(308),

    Bad_Request(400),
    Unauthorized(401),
    Forbidden(403),
    Not_Found(404),
    Method_Not_Allowed(405),
    Not_Acceptable(406),
    Proxy_Authentication_Required(407),
    Request_Timeout(408),
    Conflict(409),

    // («удалён»)
    Gone(410),
    Length_Required(411),
    Precondition_Failed(412),
    Payload_Too_Large(413),
    URI_Too_Long(414),
    Unsupported_Media_Type(415),
    Range_Not_Satisfiable(416),
    I_am_a_teapot(418),
    Locked(423),
    Precondition_Required(428),
    Too_Many_Requests(429),
    Request_Header_Fields_Too_Large(431),
    Retry_With(449),
    Client_Closed_Request(499),

    Internal_Server_Error(500),
    Not_Implemented(501),
    Bad_Gateway(502),
    Service_Unavailable(503),
    Gateway_Timeout(504),
    HTTP_Version_Not_Supported(505),
    Variant_Also_Negotiates(506),
    Insufficient_Storage(507),
    Loop_Detected(508),
    Bandwidth_Limit_Exceeded(509),
    Not_Extended(510),
    Network_Authentication_Required(511),
    Unknown_Error(520),
    Web_Server_Is_Down(521),
    Connection_Timed_Out(522),
    Origin_Is_Unreachable(523),
    A_Timeout_Occurred(524);

    public final int value;
    StatusCode(int value){
        this.value = value;
    }
}
