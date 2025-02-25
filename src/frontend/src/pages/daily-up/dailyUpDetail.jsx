import {useNavigate, useParams} from "react-router-dom";
import React, {useEffect, useState} from "react";
import axios from "axios";
import axiosInstance from "../../axiosInstance.jsx";
import {useAuth} from "../../authContext.jsx";
import './dailyUpDetail.css';

const DailyUpDetail = ({formatDate}) => {

  const {id} = useParams();
  const [dailyUp, setDailyUp] = useState({});
  const nav = useNavigate();

  useEffect(() => {
    const fetchDailyUpById = async() => {
      try {
        const response = await axios.get(`/api/daily-up/fetchById/${id}`);
        setDailyUp(response.data);
      } catch (error) {
        console.error("Error Fetching Daily Update");
      }
    };
    fetchDailyUpById();
  }, [id]);

  const convertNewlinesToHTML = (text) => {
    return text?.replace(/\n/g, "<br />");
  };
  return (
    <div className='daily-up-detail-body'>
      <div key={dailyUp.id} className='daily-up-detail-list'>
        <div className='daily-up-detail-container'>
          <h2 >{dailyUp.title}</h2>
            <p className='createdate'>{formatDate(dailyUp.postedAt)}</p>
            <div 
            className='daily-up-detail-info' 
            dangerouslySetInnerHTML={{__html: convertNewlinesToHTML(dailyUp.body)}} // HTML 렌더링
          />
          <p>{dailyUp.content}</p>
          <img src={dailyUp.imageUrl} width='200px'/>
        </div>
      </div>
    </div>
  );
}

export default DailyUpDetail;